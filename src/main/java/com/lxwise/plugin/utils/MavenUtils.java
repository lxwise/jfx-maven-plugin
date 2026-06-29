package com.lxwise.plugin.utils;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.LocationManager;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maven 项目工具类。
 * <p>
 * 提供模块路径解析、JDK jmods 定位等功能，用于自动检测项目的模块化状态
 * 并构建正确的 jpackage 参数。
 * </p>
 *
 * @author lxwise
 * @version 1.1
 * @since 2024-09
 */
public final class MavenUtils {

    private MavenUtils() {
        // 私有构造器，防止实例化
    }

    /**
     * 获取编译类路径元素列表。
     * <p>
     * 包括项目输出目录、系统依赖路径和项目运行时依赖 JAR 文件。
     * </p>
     *
     * @param project Maven 项目对象
     * @return 类路径文件列表（已去重）
     */
    public static List<File> getCompileClasspathElements(MavenProject project) {
        List<File> list = new ArrayList<>();
        list.add(new File(project.getBuild().getOutputDirectory()));

        // 添加系统作用域依赖
        list.addAll(project.getDependencies().stream()
                .filter(d -> d.getSystemPath() != null && !d.getSystemPath().isEmpty())
                .map(d -> new File(d.getSystemPath()))
                .toList());

        // 添加项目依赖 JAR
        list.addAll(project.getArtifacts().stream()
                .sorted((a1, a2) -> {
                    int compare = a1.compareTo(a2);
                    if (compare == 0) {
                        return a1.hasClassifier() ? 1 : (a2.hasClassifier() ? -1 : 0);
                    }
                    return compare;
                })
                .map(Artifact::getFile)
                .toList());

        return list.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 获取项目的 module-info.class 文件（如果存在）。
     *
     * @param project Maven 项目对象
     * @return module-info.class 文件对象；如果项目不是模块化的则返回 {@code null}
     * @throws MojoExecutionException 如果输出目录不存在或为空
     */
    public static File getModuleDescriptor(MavenProject project) throws MojoExecutionException {
        String outputDirectory = project.getBuild().getOutputDirectory();
        if (outputDirectory == null || outputDirectory.isEmpty()) {
            throw new MojoExecutionException("Error: Output directory doesn't exist");
        }

        File[] classes = new File(outputDirectory).listFiles();
        if (classes == null || classes.length == 0) {
            throw new MojoExecutionException("Error: Output directory is empty");
        }
        return Stream.of(classes)
                .filter(file -> "module-info.class".equals(file.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析模块路径，将依赖分为模块路径和类路径。
     * <p>
     * 使用 plexus-java 的 {@link LocationManager} 分析项目依赖，根据每个 JAR 是否包含
     * 模块描述符来分类为模块路径元素或类路径元素。
     * </p>
     *
     * @param locationManager plexus-java 位置管理器
     * @param project         Maven 项目对象
     * @param jdkHome         JDK 根目录（可为 {@code null}）
     * @return 解析结果，包含模块描述符、模块路径和类路径
     * @throws MojoExecutionException 如果解析过程中发生 IO 异常
     */
    public static ResolvedPaths resolveModulePaths(LocationManager locationManager, MavenProject project, File jdkHome) throws MojoExecutionException {
        File moduleDescriptorFile = getModuleDescriptor(project);
        List<File> artifacts = getCompileClasspathElements(project);

        ResolvePathsRequest<File> request = ResolvePathsRequest.ofFiles(artifacts);
        if (moduleDescriptorFile != null) {
            request.setMainModuleDescriptor(moduleDescriptorFile);
        }
        if (jdkHome != null && jdkHome.exists()) {
            request.setJdkHome(jdkHome);
        }

        try {
            ResolvePathsResult<File> result = locationManager.resolvePaths(request);

            JavaModuleDescriptor descriptor = null;
            List<String> modulepathElements = new ArrayList<>();
            List<String> classpathElements = new ArrayList<>();

            if (moduleDescriptorFile != null) {
                descriptor = result.getMainModuleDescriptor();
                result.getModulepathElements().keySet()
                        .forEach(file -> modulepathElements.add(file.getPath()));
                result.getClasspathElements()
                        .forEach(file -> classpathElements.add(file.getPath()));
            }

            return new ResolvedPaths(descriptor, modulepathElements, classpathElements);
        } catch (IOException e) {
            throw new MojoExecutionException("模块路径解析失败", e);
        }
    }

    /**
     * 查找 JDK 的 jmods 目录。
     * <p>
     * 首先检查 {@code java.home/jmods/}，然后检查 {@code java.home/../jmods/}。
     * </p>
     *
     * @return jmods 目录文件对象；如果未找到则返回 {@code null}
     */
    public static File findJdkJmodsPath() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File jdkHome = new File(javaHome);
            File jmods = new File(jdkHome, "jmods");
            if (jmods.exists()) {
                return jmods;
            }
            if (jdkHome.getParentFile() != null) {
                jmods = new File(jdkHome.getParentFile(), "jmods");
                if (jmods.exists()) {
                    return jmods;
                }
            }
        }
        return null;
    }

    /**
     * 模块路径解析结果。
     * <p>
     * 封装了解析后的模块描述符、模块路径元素列表和类路径元素列表。
     * </p>
     */
    public static class ResolvedPaths {
        private final JavaModuleDescriptor moduleDescriptor;
        private final List<String> modulepathElements;
        private final List<String> classpathElements;

        public ResolvedPaths(JavaModuleDescriptor moduleDescriptor, List<String> modulepathElements, List<String> classpathElements) {
            this.moduleDescriptor = moduleDescriptor;
            this.modulepathElements = modulepathElements;
            this.classpathElements = classpathElements;
        }

        /** 获取主模块描述符，非模块化项目为 {@code null} */
        public JavaModuleDescriptor getModuleDescriptor() {
            return moduleDescriptor;
        }

        /** 获取模块路径元素列表 */
        public List<String> getModulepathElements() {
            return modulepathElements;
        }

        /** 获取类路径元素列表 */
        public List<String> getClasspathElements() {
            return classpathElements;
        }
    }
}
