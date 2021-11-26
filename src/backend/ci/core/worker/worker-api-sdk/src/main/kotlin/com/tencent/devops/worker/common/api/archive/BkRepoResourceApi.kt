/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.worker.common.api.archive

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.collect.Maps
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.devops.artifactory.pojo.FileGatewayInfo
import com.tencent.devops.common.api.exception.RemoteServiceException
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.OkhttpUtils
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_APP_APP_TITLE
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_APP_BUNDLE_IDENTIFIER
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_APP_FULL_IMAGE
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_APP_IMAGE
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_APP_NAME
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_APP_SCHEME
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_APP_VERSION
import com.tencent.devops.process.pojo.BuildVariables
import com.tencent.devops.process.utils.PIPELINE_BUILD_NUM
import com.tencent.devops.process.utils.PIPELINE_START_USER_ID
import com.tencent.devops.worker.common.CommonEnv
import com.tencent.devops.worker.common.api.AbstractBuildResourceApi
import com.tencent.devops.worker.common.api.archive.pojo.BkRepoAccessToken
import com.tencent.devops.worker.common.api.archive.pojo.BkRepoResponse
import com.tencent.devops.worker.common.api.archive.pojo.QueryData
import com.tencent.devops.worker.common.api.archive.pojo.QueryNodeInfo
import com.tencent.devops.worker.common.api.archive.pojo.TokenType
import com.tencent.devops.worker.common.logger.LoggerService
import com.tencent.devops.worker.common.utils.IosUtils
import com.tencent.devops.worker.common.utils.TaskUtil
import net.dongliu.apk.parser.ApkFile
import okhttp3.MediaType
import okhttp3.RequestBody
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLEncoder
import java.util.Base64
import java.util.Locale

class BkRepoResourceApi : AbstractBuildResourceApi() {

    private fun getFileGateway(): FileGatewayInfo? {
        return try {
            val path = "/artifactory/api/build/fileGateway/get"
            val request = buildGet(path)
            val response = request(request, "获取构建机基本信息失败")
            val fileGatewayResult = objectMapper.readValue<Result<FileGatewayInfo>>(response)
            fileGatewayResult.data
        } catch (e: Exception) {
            logger.warn("get file gateway exception", e)
            null
        }
    }

    fun tokenAccess(): Boolean {
        var fileDevnetGateway = CommonEnv.fileDevnetGateway
        var fileIdcGateway = CommonEnv.fileIdcGateway
        if (fileDevnetGateway == null || fileIdcGateway == null) {
            val fileGatewayInfo = getFileGateway()
            fileDevnetGateway = fileGatewayInfo?.fileDevnetGateway
            CommonEnv.fileDevnetGateway = fileDevnetGateway
            fileIdcGateway = fileGatewayInfo?.fileIdcGateway
            CommonEnv.fileIdcGateway = fileIdcGateway
        }
        return fileDevnetGateway?.contains("bkrepo", true) == true &&
            fileIdcGateway?.contains("bkrepo", true) == true
    }

    fun createBkRepoTemporaryToken(
        userId: String,
        projectId: String,
        repoName: String,
        path: String,
        type: TokenType,
        expireSeconds: Long
    ): String {
        val url = "/bkrepo/api/build/generic/temporary/token/create"
        val requestData = mapOf(
            "projectId" to projectId,
            "repoName" to repoName,
            "fullPathSet" to listOf(path),
            "authorizedUserSet" to listOf<String>(),
            "authorizedIpSet" to listOf<String>(),
            "expireSeconds" to expireSeconds,
            "permits" to null,
            "type" to type.name
        )
        val request = buildPost(
            path = url,
            requestBody = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                objectMapper.writeValueAsString(requestData)
            ),
            headers = mapOf(BKREPO_UID to userId)
        )
        OkhttpUtils.doHttp(request).use { response ->
            val responseContent = response.body()!!.string()
            if (!response.isSuccessful) {
                logger.error("http request failed, code: ${response.code()}, responseContent: $responseContent")
                throw RemoteServiceException("http request failed", response.code())
            }

            val responseData = objectMapper.readValue<BkRepoResponse<List<BkRepoAccessToken>>>(responseContent)
            if (responseData.isNotOk()) {
                throw RemoteServiceException("request failed: ${responseData.message}")
            }

            return responseData.data!![0].token
        }
    }

    fun uploadFileByToken(
        file: File,
        projectId: String,
        repoName: String,
        destFullPath: String,
        token: String,
        buildVariables: BuildVariables,
        parseAppMetadata: Boolean = true
    ) {
        val url = "/generic/temporary/upload/$projectId/$repoName$destFullPath?token=$token"
        val request = buildPut(
            url,
            RequestBody.create(MediaType.parse("application/octet-stream"), file),
            getUploadHeader(file, buildVariables, parseAppMetadata),
            useFileDevnetGateway = TaskUtil.isVmBuildEnv(buildVariables.containerType)
        )
        val response = request(request, "上传文件失败")
        try {
            val obj = objectMapper.readTree(response)
            if (obj.has("code") && obj["code"].asText() != "0")
                throw RemoteServiceException("上传文件失败")
        } catch (e: Exception) {
            LoggerService.addErrorLine(e.message ?: "")
            throw RemoteServiceException("上传文件失败: $response")
        }
    }

    fun downloadFileByToken(
        userId: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        token: String,
        destPath: File,
        isVmBuildEnv: Boolean
    ) {
        val url = "/generic/temporary/download/$projectId/$repoName$fullPath?token=$token"
        val header = HashMap<String, String>()
        header[BKREPO_UID] = userId
        val request = buildGet(url, header, isVmBuildEnv)
        download(request, destPath)
    }

    private fun directDownloadBkRepoFile(
        user: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        destPath: File
    ) {
        val url = "/bkrepo/api/build/generic/$projectId/$repoName$fullPath"
        val header = HashMap<String, String>()
        header[BKREPO_UID] = user
        val request = buildGet(url, header, true)
        download(request, destPath)
    }

    fun uploadBkRepoFile(
        file: File,
        repoName: String,
        destFullPath: String,
        buildVariables: BuildVariables,
        parseAppMetadata: Boolean,
        token: String? = null
    ) {
        if (!token.isNullOrBlank()) {
            uploadFileByToken(
                file = file,
                projectId = buildVariables.projectId,
                repoName = repoName,
                destFullPath = destFullPath,
                token = token,
                buildVariables = buildVariables,
                parseAppMetadata = parseAppMetadata
            )
        } else {
            directUploadBkRepoFile(
                file = file,
                projectId = buildVariables.projectId,
                repoName = repoName,
                destFullPath = destFullPath,
                buildVariables = buildVariables,
                parseAppMetadata = parseAppMetadata
            )
        }
    }

    fun downloadBkRepoFile(
        userId: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        destPath: File,
        token: String? = null
    ) {
        if (tokenAccess()) {
            downloadFileByToken(
                userId = userId,
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                token = token!!,
                destPath = destPath,
                isVmBuildEnv = true
            )
        } else {
            directDownloadBkRepoFile(userId, projectId, repoName, fullPath, destPath)
        }
    }

    private fun directUploadBkRepoFile(
        file: File,
        projectId: String,
        repoName: String,
        destFullPath: String,
        buildVariables: BuildVariables,
        parseAppMetadata: Boolean = false
    ) {
        logger.info("upload file >>> ${file.name}")
        val url = "/bkrepo/api/build/generic/$projectId/$repoName$destFullPath"
        val request = buildPut(
            path = url,
            requestBody = RequestBody.create(MediaType.parse("application/octet-stream"), file),
            headers = getUploadHeader(file, buildVariables, parseAppMetadata),
            useFileDevnetGateway = true
        )
        val response = request(request, "上传文件失败")
        try {
            val obj = objectMapper.readTree(response)
            if (obj.has("code") && obj["code"].asText() != "0")
                throw RemoteServiceException("上传文件失败")
        } catch (e: Exception) {
            LoggerService.addErrorLine(e.message ?: "")
            throw RemoteServiceException("上传文件失败: $response")
        }
    }

    fun getUploadHeader(
        file: File,
        buildVariables: BuildVariables,
        parseAppMetadata: Boolean = true
    ): HashMap<String, String> {
        val header = Maps.newHashMap<String, String>()
        header[BKREPO_UID] = buildVariables.variables[PIPELINE_START_USER_ID] ?: ""
        header[BKREPO_OVERRIDE] = "true"

        val metadata = mutableMapOf<String, String>()
        metadata[ARCHIVE_PROPS_PROJECT_ID] = buildVariables.projectId
        metadata[ARCHIVE_PROPS_PIPELINE_ID] = buildVariables.pipelineId
        metadata[ARCHIVE_PROPS_BUILD_ID] = buildVariables.buildId
        metadata[ARCHIVE_PROPS_USER_ID] = buildVariables.variables[PIPELINE_START_USER_ID] ?: ""
        metadata[ARCHIVE_PROPS_BUILD_NO] = buildVariables.variables[PIPELINE_BUILD_NUM] ?: ""
        metadata[ARCHIVE_PROPS_SOURCE] = "pipeline"
        metadata[ARCHIVE_PROPS_TASK_ID] = TaskUtil.getTaskId()
        if (parseAppMetadata) {
            metadata.putAll(getAppMetadata(file))
        }
        header[BKREPO_METADATA] = Base64.getEncoder().encodeToString(buildMetadataHeader(metadata).toByteArray())
        return header
    }

    private fun buildMetadataHeader(metadata: Map<String, String>): String {
        return StringUtils.join(
            metadata.map {
                "${urlEncode(it.key)}=${urlEncode(it.value)}"
            },
            "&"
        )
    }

    private fun urlEncode(str: String?): String {
        return if (str.isNullOrBlank()) {
            ""
        } else {
            URLEncoder.encode(str, "UTF-8")
        }
    }

    private fun getAppMetadata(file: File): Map<String, String> {
        try {
            return when {
                file.name.endsWith(".ipa") -> {
                    val map = IosUtils.getIpaInfoMap(file)
                    val result = mutableMapOf(
                        ARCHIVE_PROPS_APP_VERSION to (map["bundleVersion"] ?: ""),
                        ARCHIVE_PROPS_APP_BUNDLE_IDENTIFIER to (map["bundleIdentifier"] ?: ""),
                        ARCHIVE_PROPS_APP_APP_TITLE to (map["appTitle"] ?: ""),
                        ARCHIVE_PROPS_APP_IMAGE to (map["image"] ?: ""),
                        ARCHIVE_PROPS_APP_FULL_IMAGE to (map["fullImage"] ?: ""),
                        ARCHIVE_PROPS_APP_SCHEME to (map["scheme"] ?: ""),
                        ARCHIVE_PROPS_APP_NAME to (map["appName"] ?: "")
                    )
                    result
                }
                file.name.endsWith(".apk") -> {
                    val apkFile = ApkFile(file)
                    apkFile.preferredLocale = Locale.SIMPLIFIED_CHINESE
                    val meta = apkFile.apkMeta
                    val result = mutableMapOf(
                        ARCHIVE_PROPS_APP_VERSION to meta.versionName,
                        ARCHIVE_PROPS_APP_APP_TITLE to meta.name,
                        ARCHIVE_PROPS_APP_BUNDLE_IDENTIFIER to meta.packageName,
                        ARCHIVE_PROPS_APP_NAME to (meta.label ?: "")
                    )
                    result
                }
                else -> {
                    mapOf()
                }
            }
        } catch (e: Exception) {
            logger.warn("get metadata from file(${file.absolutePath}) failed", e)
            return mapOf()
        }
    }

    fun setPipelineMetadata(repoName: String, buildVariables: BuildVariables) {
        try {
            val userId = buildVariables.variables[PIPELINE_START_USER_ID] ?: ""
            val projectId = buildVariables.projectId
            val pipelineId = buildVariables.pipelineId
            val pipelineName = buildVariables.variables[BK_CI_PIPELINE_NAME]
            val buildId = buildVariables.buildId
            val buildNum = buildVariables.variables[BK_CI_BUILD_NUM]
            val headers = mapOf(BKREPO_UID to userId)
            if (!pipelineName.isNullOrBlank()) {
                val pipelineNameRequest = buildPost(
                    "/bkrepo/api/build/repository/api/metadata/$projectId/$repoName/$pipelineId",
                    RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        JsonUtil.toJson(mapOf("metadata" to mapOf(METADATA_DISPLAY_NAME to pipelineName)))
                    ),
                    headers
                )
                request(pipelineNameRequest, "set pipeline displayName failed")
            }
            if (!buildNum.isNullOrBlank()) {
                val buildNumRequest = buildPost(
                    "/bkrepo/api/build/repository/api/metadata/$projectId/$repoName/$pipelineId/$buildId",
                    RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        JsonUtil.toJson(mapOf("metadata" to mapOf(METADATA_DISPLAY_NAME to buildNum)))
                    ),
                    headers
                )
                request(buildNumRequest, "set build displayName failed")
            }
        } catch (e: Exception) {
            logger.warn("set pipeline metadata error: ${e.message}")
        }
    }

    fun queryByPathEqOrNameMatchOrMetadataEqAnd(
        userId: String,
        projectId: String, // eq
        repoNames: List<String>, // eq or
        filePaths: List<String>, // eq or
        fileNames: List<String>, // match or
        metadata: Map<String, String>, // eq and
        page: Int,
        pageSize: Int
    ): List<QueryNodeInfo> {
        logger.info("queryByPathEqOrNameMatchOrMetadataEqAnd, userId: $userId, projectId: $projectId, " +
            "repoNames: $repoNames, filePaths: $filePaths, fileNames: $fileNames, metadata: $metadata, " +
            "page: $page, pageSize: $pageSize")
        val projectRule = Rule.QueryRule("projectId", projectId, OperationType.EQ)
        val repoRule = Rule.QueryRule("repoName", repoNames, OperationType.IN)
        val ruleList = mutableListOf<Rule>(projectRule, repoRule, Rule.QueryRule("folder", false, OperationType.EQ))
        if (filePaths.isNotEmpty()) {
            val filePathRule = Rule.NestedRule(
                filePaths.map { Rule.QueryRule("path", it, OperationType.EQ) }.toMutableList(),
                Rule.NestedRule.RelationType.OR
            )
            ruleList.add(filePathRule)
        }
        if (fileNames.isNotEmpty()) {
            val fileNameRule = Rule.NestedRule(
                fileNames.map { Rule.QueryRule("name", it, OperationType.MATCH) }.toMutableList(),
                Rule.NestedRule.RelationType.OR
            )
            ruleList.add(fileNameRule)
        }
        if (metadata.isNotEmpty()) {
            val metadataRule =
                Rule.NestedRule(metadata.map { Rule.QueryRule("metadata.${it.key}", it.value, OperationType.EQ) }
                    .toMutableList(), Rule.NestedRule.RelationType.AND)
            ruleList.add(metadataRule)
        }
        val rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)

        return query(userId, projectId, rule, page, pageSize)
    }

    private fun query(userId: String, projectId: String, rule: Rule, page: Int, pageSize: Int): List<QueryNodeInfo> {
        // logger.info("query, userId: $userId, rule: $rule, page: $page, pageSize: $pageSize")
        val url = "/bkrepo/api/build/repository/api/node/query"
        val queryModel = QueryModel(
            page = PageLimit(page, pageSize),
            sort = Sort(listOf("fullPath"), Sort.Direction.ASC),
            select = mutableListOf(),
            rule = rule
        )

        val requestBody = objectMapper.writeValueAsString(queryModel)
        // logger.info("requestBody: $requestBody")
        val request = buildPost(
            url,
            RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                requestBody
            ),
            mutableMapOf("X-BKREPO-UID" to userId)
        )
        OkhttpUtils.doHttp(request).use { response ->
            val responseContent = response.body()!!.string()
            if (!response.isSuccessful) {
                logger.error("query failed, responseContent: $responseContent")
                throw RemoteServiceException("query failed", response.code())
            }

            val responseData = objectMapper.readValue<Response<QueryData>>(responseContent)
            if (responseData.isNotOk()) {
                throw RemoteServiceException("query failed: ${responseData.message}")
            }

            return responseData.data!!.records
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkRepoResourceApi::class.java)
        private const val BKREPO_METADATA = "X-BKREPO-META"
        private const val BKREPO_UID = "X-BKREPO-UID"
        private const val BKREPO_OVERRIDE = "X-BKREPO-OVERWRITE"

        private const val BK_CI_PIPELINE_NAME = "BK_CI_PIPELINE_NAME"
        private const val BK_CI_BUILD_NUM = "BK_CI_BUILD_NUM"
        private const val METADATA_DISPLAY_NAME = "displayName"
    }
}
