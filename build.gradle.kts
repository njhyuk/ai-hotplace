plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.4.5"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.njhyuk"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	
	// Coroutines (for runBlocking)
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
	
	// Selenium
	implementation("org.seleniumhq.selenium:selenium-java:4.18.1")
	implementation("io.github.bonigarcia:webdrivermanager:5.7.0")
	
	// OpenAI
	implementation("com.aallam.openai:openai-client:3.7.0")
	implementation("io.ktor:ktor-client-okhttp:2.3.9")
	
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.bootJar {
	enabled = true
}

tasks.jar {
	enabled = false
}
