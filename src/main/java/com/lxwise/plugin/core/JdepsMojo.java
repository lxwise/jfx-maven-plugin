package com.lxwise.plugin.core;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目依赖分析 Mojo。
 * <p>
 * 使用 JDK 内置的 {@code jdeps} 工具分析项目依赖，输出所需的 JDK 模块名（逗号分隔），
 * 可用于精简非模块化项目的 JRE。执行 {@code mvn jfx:jdeps} 目标即可触发分析。
 * </p>
 * <p>
 * 分析结果可直接配置到 {@code addModules} 参数中，配合 {@code jlinkOptions} 使用
 * 可显著减小打包体积。
 * </p>
 *
 * @author lxwise
 * @version 1.1
 * @since 2024-09
 */
@Mojo(name = "jdeps", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.PACKAGE)
public class JdepsMojo extends AbstractMojo {

    /** Maven 项目对象，由框架自动注入 */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * 多版本 JAR 的版本号。
     * <p>不设置则自动检测当前 Java 版本。</p>
     */
    @Parameter(property = "jdeps.multiRelease", defaultValue = "")
    private String multiRelease;

    /**
     * 插件执行入口方法。
     * <p>
     * 执行流程：
     * <ol>
     *     <li>定位主 JAR 文件</li>
     *     <li>检测或设置多版本号</li>
     *     <li>构建 jdeps 命令行并执行</li>
     *     <li>输出分析结果（逗号分隔的模块名）</li>
     * </ol>
     * </p>
     *
     * @throws MojoExecutionException 如果 jdeps 执行失败
     * @throws MojoFailureException   如果构建逻辑失败
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String directory = project.getBuild().getDirectory();
        String finalName = project.getBuild().getFinalName() + "." + project.getPackaging();
        File mainJar = new File(directory, finalName);

        if (!mainJar.exists()) {
            throw new MojoExecutionException("主 JAR 不存在: " + mainJar.getAbsolutePath());
        }

        String release = multiRelease;
        if (StringUtils.isEmpty(release)) {
            release = detectJavaVersion();
        }

        List<String> command = new ArrayList<>();
        command.add("jdeps");
        command.add("--ignore-missing-deps");
        if (StringUtils.isNotEmpty(release)) {
            command.add("--multi-release");
            command.add(release);
        }
        command.add("--print-module-deps");

        // 将所有依赖 JAR 放入 class-path
        Set<Artifact> artifacts = project.getArtifacts();
        if (artifacts != null && !artifacts.isEmpty()) {
            String classPath = artifacts.stream()
                    .map(Artifact::getFile)
                    .filter(f -> f != null && f.exists())
                    .map(File::getAbsolutePath)
                    .collect(Collectors.joining(File.pathSeparator));
            if (!classPath.isEmpty()) {
                command.add("--class-path");
                command.add(classPath);
            }
        }

        command.add(mainJar.getAbsolutePath());

        getLog().info("执行指令: " + String.join(" ", command));

        CommandLine cmd = CommandLine.parse(String.join(" ", command));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(new PumpStreamHandler(outputStream, errorStream));

        try {
            executor.execute(cmd);
            String warnings = errorStream.toString().trim();
            if (!warnings.isEmpty()) {
                getLog().warn(warnings);
            }
            String output = outputStream.toString().trim();
            if (output.isEmpty()) {
                getLog().warn("jdeps 未输出任何模块依赖");
                return;
            }

            List<String> modules = Arrays.stream(output.split("\\R"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            String result = String.join(",", modules);
            getLog().info("模块依赖: " + result);
            System.out.println(result);
        } catch (IOException e) {
            String errorMsg = errorStream.toString().trim();
            if (!errorMsg.isEmpty()) {
                getLog().error(errorMsg);
            }
            throw new MojoExecutionException("jdeps 执行失败", e);
        }
    }

    /**
     * 自动检测当前 Java 版本号。
     *
     * @return Java 主版本号字符串（如 "21"），检测失败返回空字符串
     */
    private String detectJavaVersion() {
        String version = System.getProperty("java.version");
        if (version == null) {
            return "";
        }
        if (version.startsWith("1.")) {
            return version.substring(2, 3);
        }
        int dot = version.indexOf('.');
        if (dot > 0) {
            return version.substring(0, dot);
        }
        int dash = version.indexOf('-');
        if (dash > 0) {
            return version.substring(0, dash);
        }
        return version;
    }
}
