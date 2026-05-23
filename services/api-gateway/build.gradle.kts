plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

// Spring Cloud Gateway is reactive (built on Netty + WebFlux). We don't add
// spring-boot-starter-web here — Gateway brings WebFlux transitively and the two
// stacks shouldn't both be on the classpath.
dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.dependencies.get().toString())
    }
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.actuator)
    testImplementation(libs.spring.boot.starter.test)
}
