dependencies {
    api(project(":core:common:common-service"))
    api(project(":core:common:common-web"))
    api(project(":core:common:common-client"))
    api(project(":core:common:common-redis"))
    api(project(":core:common:common-util"))
    api(project(":core:apiquery:model-apiquery"))
    api(project(":core:apiquery:api-apiquery"))
    api(project(":core:defect:api-defect"))
    api(project(":core:task:api-task"))
    api(project(":core:common:common-auth:common-auth-api"))
    api(project(":core:schedule:api-schedule"))
    api("io.jsonwebtoken:jjwt")
    api(group = "net.sf.json-lib", name = "json-lib", classifier = "jdk15")
    api("com.alibaba:easyexcel:2.2.7")
}
