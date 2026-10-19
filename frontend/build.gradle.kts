// The frontend is built with npm; these tasks only hook it into the Gradle build.

val npmInstall by tasks.registering(Exec::class) {
    group = "frontend"
    commandLine("npm", "ci")
    inputs.files("package.json", "package-lock.json")
    outputs.dir("node_modules")
}

val lint by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(npmInstall)
    commandLine("npm", "run", "lint")
}

val npmBuild by tasks.registering(Exec::class) {
    group = "build"
    dependsOn(npmInstall)
    commandLine("npm", "run", "build")
    inputs.files("package.json", "package-lock.json", "index.html", "vite.config.ts", "tsconfig.json", "tsconfig.app.json", "tsconfig.node.json")
    inputs.dir("src")
    inputs.dir("public")
    outputs.dir("dist")
}

tasks.register("check") {
    group = "verification"
    dependsOn(lint)
}

tasks.register("build") {
    group = "build"
    dependsOn("check", npmBuild)
}

tasks.register<Delete>("clean") {
    group = "build"
    delete("dist")
}
