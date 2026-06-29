package com.lxwise.plugin.core;

import com.lxwise.plugin.constant.CommonConstant;
import com.lxwise.plugin.utils.FileUtils;
import com.lxwise.plugin.utils.MavenUtils;
import com.lxwise.plugin.utils.PlatformUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.LocationManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JavaFX 应用打包 Maven 插件核心 Mojo。
 * <p>
 * 基于 JDK 内置的 {@code jpackage} 工具，将 JavaFX 应用程序打包为各平台原生安装包。
 * 支持三种打包模式：
 * <ul>
 *     <li><b>非模块化模式</b>：无 {@code module-info.class} 时，拷贝 JAR 到 lib 目录，使用 {@code --input}/{@code --main-jar}/{@code --main-class}</li>
 *     <li><b>模块化模式</b>：有 {@code module-info.class} 时，使用 {@code --module-path}/{@code --add-modules}/{@code --module}，自动精简 JRE</li>
 *     <li><b>预构建运行时模式</b>：配置了 {@code imagePath} 时，使用 {@code --runtime-image}/{@code --module}</li>
 * </ul>
 * 支持 Windows（exe、msi）、macOS（pkg、dmg）和 Linux（rpm、deb）等格式。
 * </p>
 * <p>
 * 使用方式：在 Maven 中执行 {@code mvn jfx:package} 目标即可触发打包流程。
 * 插件会自动检测项目的模块化状态，选择对应的打包方式。
 * </p>
 *
 * @author lxwise
 * @version 1.1
 * @since 2024-09
 */
@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.PACKAGE)
public class JPackageMojo extends AbstractMojo {

    // ==================== Maven 注入参数 ====================

    /** Maven 项目对象，由框架自动注入 */
    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    // ==================== 核心配置参数 ====================

    /** 应用程序入口类的全限定名（必填） */
    @Parameter(required = true)
    private String mainClass;

    /**
     * 预构建的运行时镜像路径。
     * <p>若指定此参数，将使用 {@code --runtime-image} 模式打包，跳过依赖复制和自动模块检测。</p>
     */
    @Parameter(defaultValue = "")
    private String imagePath;

    /** 传递给应用程序 JVM 的选项列表，如 {@code -Xmx512m} */
    @Parameter
    private List<String> options = new ArrayList<>();

    /** 传递给应用程序主类的命令行参数列表 */
    @Parameter
    private List<String> arguments = new ArrayList<>();

    // ==================== 模块化相关配置 ====================

    /**
     * 要包含在运行时镜像中的额外 JDK 模块，多个模块使用逗号分隔。
     * <p>
     * 例如：{@code java.base,java.desktop,java.sql}。
     * 可通过 {@code mvn jfx:jdeps} 分析项目所需的模块列表。
     * </p>
     */
    @Parameter(defaultValue = "")
    private String addModules;

    /**
     * 传递给 jlink 的选项列表，用于控制运行时镜像的生成。
     * <p>
     * 例如：{@code --strip-debug}、{@code --compress zip-9} 等。
     * 与 {@code imagePath} 互斥。
     * </p>
     */
    @Parameter
    private List<String> jlinkOptions = new ArrayList<>();

    /**
     * 额外的模块路径列表。
     * <p>用于指定包含模块 JAR 文件的目录路径。</p>
     */
    @Parameter
    private List<String> modulePath = new ArrayList<>();

    /**
     * JDK jmods 目录的路径。
     * <p>若不设置则自动从 {@code java.home} 检测。</p>
     */
    @Parameter(defaultValue = "")
    private String jmodsPath;

    // ==================== 通用打包配置 ====================

    /**
     * 打包输出类型。
     * <p>可选值：{@code app-image}、{@code exe}、{@code msi}、{@code rpm}、{@code deb}、{@code pkg}、{@code dmg}</p>
     */
    @Parameter(defaultValue = "")
    private String type;

    /** 应用程序版本号，默认使用 Maven 项目版本 */
    @Parameter(defaultValue = "${project.version}")
    private String appVersion;

    /** 版权信息 */
    @Parameter(defaultValue = "")
    private String copyright;

    /** 应用程序描述 */
    @Parameter(defaultValue = "")
    private String description;

    /** 应用程序图标路径（支持绝对路径或相对于项目根目录的相对路径） */
    @Parameter(defaultValue = "")
    private String icon;

    /** 应用程序名称，默认使用 Maven 项目名称 */
    @Parameter(defaultValue = "")
    private String name;

    /** 应用程序厂商/供应商名称 */
    @Parameter(defaultValue = "")
    private String vendor;

    /** 是否启用 jpackage 详细输出，默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean verbose;

    /** 应用程序相关信息的 URL */
    @Parameter(defaultValue = "")
    private String aboutUrl;

    /** 安装目录的绝对路径 */
    @Parameter(defaultValue = "")
    private String installDir;

    // ==================== macOS 专属配置 ====================

    /** macOS 软件包标识符（如 {@code com.example.app}） */
    @Parameter(defaultValue = "")
    private String macPackageIdentifier;

    /**
     * macOS 菜单栏中显示的应用名称。
     * <p>长度必须小于 16 个字符，默认使用应用名称。</p>
     */
    @Parameter(defaultValue = "")
    private String macPackageName;

    /** macOS 软件包签名前缀 */
    @Parameter(defaultValue = "")
    private String macPackageSigningPrefix;

    /** macOS 签名标志 */
    @Parameter(defaultValue = "")
    private String macSign;

    /** macOS 签名钥匙串路径 */
    @Parameter(defaultValue = "")
    private String macSigningKeychain;

    /** macOS 签名密钥用户名 */
    @Parameter(defaultValue = "")
    private String macSigningKeyUserName;

    /** 是否面向 Mac App Store 发布，默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean macAppStore;

    /** macOS 权利文件路径（entitlements 文件） */
    @Parameter(defaultValue = "")
    private String macEntitlements;

    /** macOS 应用类别（如 {@code public.app-category.developer-tools}） */
    @Parameter(defaultValue = "")
    private String macAppCategory;

    // ==================== Windows 专属配置 ====================

    /** 是否创建控制台启动器（适用于需要控制台交互的应用），默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean winConsole;

    /** 安装时是否允许用户选择安装目录，默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean winDirChooser;

    /** 帮助/技术支持页面的 URL */
    @Parameter(defaultValue = "")
    private String winHelpUrl;

    /** 是否添加开始菜单快捷方式，默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean winMenu;

    /** 开始菜单中的分组名称 */
    @Parameter(defaultValue = "")
    private String winMenuGroup;

    /** 是否按用户安装（非系统级），默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean winPerUserInstall;

    /** 是否创建桌面快捷方式，默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean winShortcut;

    /** 安装时是否弹出快捷方式创建确认对话框，默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean winShortcutPrompt;

    /** 应用程序更新信息的 URL */
    @Parameter(defaultValue = "")
    private String winUpdateUrl;

    /** 与 MSI 升级关联的 UUID */
    @Parameter(defaultValue = "")
    private String winUpgradeUuid;

    // ==================== Linux 专属配置 ====================

    /** Linux 软件包名称，默认使用应用名称 */
    @Parameter(defaultValue = "")
    private String linuxPackageName;

    /** .deb 软件包的维护者邮箱地址 */
    @Parameter(defaultValue = "")
    private String linuxDebMaintainer;

    /** Linux 菜单组名称 */
    @Parameter(defaultValue = "")
    private String linuxMenuGroup;

    /** 是否为 Linux 应用创建桌面快捷方式，默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean linuxShortcut;

    // ==================== 内部工作变量 ====================

    /** 构建工作目录的绝对路径 */
    private String workDirectory;

    /** 平台相关资源目录的绝对路径 */
    private String resourceDirectory;

    /** 主 JAR 文件的路径，在依赖复制阶段自动设置 */
    private String mainJar;

    /** plexus-java 位置管理器，用于模块路径解析 */
    @Component
    private LocationManager locationManager;

    /** 模块描述符，非模块化项目为 null */
    private JavaModuleDescriptor moduleDescriptor;

    /** 模块路径元素列表 */
    private List<String> modulepathElements = new ArrayList<>();

    /** 类路径元素列表 */
    private List<String> classpathElements = new ArrayList<>();

    // ==================== 执行入口 ====================

    /**
     * 插件执行入口方法。
     * <p>
     * 执行流程：
     * <ol>
     *     <li>初始化工作目录和资源目录</li>
     *     <li>自动检测模块化状态，复制依赖或解析模块路径</li>
     *     <li>构建 jpackage 命令行</li>
     *     <li>执行 jpackage 命令完成打包</li>
     *     <li>重命名构建产物</li>
     *     <li>清理临时 lib 目录</li>
     * </ol>
     * </p>
     *
     * @throws MojoExecutionException 如果初始化或命令执行失败
     * @throws MojoFailureException   如果构建逻辑失败
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("开始执行 jfx:package...");
        init();

        DefaultExecutor executor = new DefaultExecutor();
        CommandLine command = buildCommand();
        try {
            executor.execute(command);
            renameOutput();
        } catch (IOException e) {
            throw new MojoExecutionException("jpackage 指令执行失败", e);
        } finally {
            cleanLib();
        }
    }

    // ==================== 初始化逻辑 ====================

    /**
     * 初始化打包环境：清理旧文件、创建工作目录、定位资源目录、解析模块路径或复制依赖。
     *
     * @throws MojoExecutionException 如果任何初始化步骤失败
     */
    private void init() throws MojoExecutionException {
        clear();
        this.workDirectory = createWorkDirectory();
        initResourceDir();

        if (StringUtils.isEmpty(imagePath)) {
            File jdkHome = findJdkHome();
            MavenUtils.ResolvedPaths resolved = MavenUtils.resolveModulePaths(locationManager, project, jdkHome);
            this.moduleDescriptor = resolved.getModuleDescriptor();
            this.modulepathElements = resolved.getModulepathElements();
            this.classpathElements = resolved.getClasspathElements();

            if (moduleDescriptor == null) {
                // 非模块化项目：复制依赖到 lib 目录
                copyLibrary();
            }
        }
    }

    /**
     * 根据当前操作系统初始化平台资源目录。
     */
    private void initResourceDir() {
        File resource = new File(project.getBasedir(), CommonConstant.JAVAFX);
        if (!resource.exists()) {
            return;
        }

        String platformDir;
        if (SystemUtils.IS_OS_WINDOWS) {
            platformDir = CommonConstant.WINDOWS;
        } else if (SystemUtils.IS_OS_MAC) {
            platformDir = CommonConstant.MAC;
        } else if (SystemUtils.IS_OS_LINUX) {
            platformDir = CommonConstant.LINUX;
        } else {
            return;
        }

        File platformResource = new File(resource, platformDir);
        if (platformResource.exists()) {
            this.resourceDirectory = platformResource.getAbsolutePath();
        }
    }

    // ==================== 构建产物重命名 ====================

    /**
     * 重命名构建产物，添加平台和架构信息。
     * <p>格式：{name}-{platform}-{arch}-{version}[.extension]</p>
     */
    private void renameOutput() {
        String targetName = this.name + "-" + PlatformUtils.platform() + "-" + PlatformUtils.arch() + "-" + appVersion;
        File outputDir = new File(workDirectory);

        File original = findBuildArtifact(outputDir);
        if (original == null) {
            getLog().warn("未找到构建物，跳过重命名");
            return;
        }

        String extension = getExtension(original.getName());
        if (StringUtils.isNotEmpty(extension)) {
            targetName = targetName + "." + extension;
        }

        if (original.exists()) {
            if (original.renameTo(new File(outputDir, targetName))) {
                getLog().info("构建物已重命名: " + original.getName() + " -> " + targetName);
            } else {
                getLog().warn("重命名失败: " + original.getName() + " -> " + targetName);
            }
        }
    }

    /**
     * 在输出目录中查找构建产物（排除 lib 目录）。
     */
    private File findBuildArtifact(File outputDir) {
        if (!outputDir.exists()) {
            return null;
        }
        File[] files = outputDir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (!CommonConstant.LIB.equals(file.getName())) {
                return file;
            }
        }
        return null;
    }

    /**
     * 获取文件扩展名。
     */
    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }

    // ==================== 命令构建 ====================

    /**
     * 构建完整的 jpackage 命令行。
     *
     * @return 解析后的 {@link CommandLine} 对象
     */
    private CommandLine buildCommand() {
        List<String> command = new ArrayList<>();
        command.add("jpackage");

        // 输出目录
        addQuotedOption(command, "--dest", workDirectory);

        // 打包类型
        if (StringUtils.isNotEmpty(type)) {
            addQuotedOption(command, "--type", type);
        }

        // 版本号
        command.add("--app-version");
        command.add(appVersion);

        // 通用选项
        addQuotedOptionIfPresent(command, "--copyright", copyright);
        addQuotedOptionIfPresent(command, "--description", description);

        // 图标（需要路径校验）
        addFileOption(command, "--icon", icon, "icon 文件不存在: ");

        // 应用名称（默认使用项目名称）
        if (StringUtils.isEmpty(name)) {
            name = project.getName();
        }
        addQuotedOption(command, "--name", name);

        addQuotedOptionIfPresent(command, "--vendor", vendor);

        if (Boolean.TRUE.equals(verbose)) {
            command.add("--verbose");
        }

        if (StringUtils.isNotEmpty(aboutUrl) && !isAppImage()) {
            addQuotedOption(command, "--about-url", aboutUrl);
        }
        addQuotedOptionIfPresent(command, "--install-dir", installDir);
        addQuotedOptionIfPresent(command, "--resource-dir", resourceDirectory);

        // jlinkOptions 与 imagePath 互斥检查
        if (jlinkOptions != null && !jlinkOptions.isEmpty() && StringUtils.isNotEmpty(imagePath)) {
            getLog().warn("选项 [imagePath] 和 [jlinkOptions] 相互排斥");
        }

        // 输入模式：三种模式
        if (StringUtils.isEmpty(imagePath)) {
            if (moduleDescriptor != null) {
                // === 模块化模式 ===
                appendModularMode(command);
            } else {
                // === 非模块化模式 ===
                appendNonModularMode(command);
            }
        } else {
            // === 预构建运行时模式 ===
            appendRuntimeImageMode(command);
        }

        // 应用参数和 JVM 选项
        addRepeatedOption(command, "--arguments", arguments);
        addRepeatedQuotedOption(command, "--java-options", options);

        // 平台特定选项
        appendWindowsOptions(command);
        appendMacOptions(command);
        appendLinuxOptions(command);

        getLog().info("执行指令: " + String.join(" ", command));
        return CommandLine.parse(String.join(" ", command));
    }

    /**
     * 追加模块化模式的 jpackage 参数。
     * <p>
     * 使用 {@code --module-path}、{@code --add-modules}、{@code --module} 和可选的
     * {@code --jlink-options} 来构建精简的运行时镜像。
     * </p>
     */
    private void appendModularMode(List<String> command) {
        String modulePathStr = buildModulePathString();
        command.add("--module-path");
        command.add("\"" + modulePathStr + "\"");

        String addModulesValue = moduleDescriptor.name();
        if (StringUtils.isNotEmpty(addModules)) {
            addModulesValue = addModulesValue + "," + addModules;
        }
        command.add("--add-modules");
        command.add("\"" + addModulesValue + "\"");

        String moduleArg = formatModuleMainClass();
        command.add("--module");
        command.add("\"" + moduleArg + "\"");

        if (jlinkOptions != null && !jlinkOptions.isEmpty()) {
            jlinkOptions.forEach(option -> {
                command.add("--jlink-options");
                command.add("\"" + option + "\"");
            });
        }
    }

    /**
     * 追加非模块化模式的 jpackage 参数。
     * <p>
     * 使用 {@code --input}、{@code --main-class}、{@code --main-jar}，并可选
     * {@code --add-modules}、{@code --module-path}、{@code --jlink-options}。
     * </p>
     */
    private void appendNonModularMode(List<String> command) {
        addQuotedOption(command, "--input", new File(workDirectory, CommonConstant.LIB).getAbsolutePath());
        addQuotedOption(command, "--main-class", mainClass);
        addQuotedOption(command, "--main-jar", mainJar);

        if (StringUtils.isNotEmpty(addModules)) {
            command.add("--add-modules");
            command.add("\"" + addModules + "\"");
        }
        if (modulePath != null && !modulePath.isEmpty()) {
            modulePath.forEach(option -> {
                command.add("--module-path");
                command.add("\"" + option + "\"");
            });
        }
        if (jlinkOptions != null && !jlinkOptions.isEmpty()) {
            jlinkOptions.forEach(option -> {
                command.add("--jlink-options");
                command.add("\"" + option + "\"");
            });
        }
    }

    /**
     * 追加预构建运行时镜像模式的 jpackage 参数。
     * <p>使用 {@code --runtime-image} 和 {@code --module}。</p>
     */
    private void appendRuntimeImageMode(List<String> command) {
        File image = path(imagePath);
        addQuotedOption(command, "--runtime-image", image.getAbsolutePath());
        command.add("--module");
        command.add("\"" + mainClass + "\"");
    }

    // ==================== 模块化辅助方法 ====================

    /**
     * 构建模块路径字符串。
     * <p>
     * 合并 JDK jmods 目录、自动解析的模块路径元素和用户指定的额外模块路径。
     * </p>
     */
    private String buildModulePathString() {
        List<String> parts = new ArrayList<>();

        // JDK jmods 路径
        String jmodsPathValue = this.jmodsPath;
        if (StringUtils.isEmpty(jmodsPathValue)) {
            File autoDetected = MavenUtils.findJdkJmodsPath();
            if (autoDetected != null) {
                jmodsPathValue = autoDetected.getAbsolutePath();
            }
        }
        if (StringUtils.isNotEmpty(jmodsPathValue)) {
            parts.add(jmodsPathValue);
        }

        // 自动解析的模块路径
        parts.addAll(modulepathElements);

        // 用户指定的额外模块路径
        if (modulePath != null && !modulePath.isEmpty()) {
            parts.addAll(modulePath);
        }

        return String.join(File.pathSeparator, parts);
    }

    /**
     * 格式化模块主类参数。
     * <p>格式：{@code moduleName/com.example.MainClass}</p>
     */
    private String formatModuleMainClass() {
        if (mainClass.contains("/")) {
            return mainClass;
        }
        return moduleDescriptor.name() + "/" + mainClass;
    }

    /**
     * 查找 JDK 根目录。
     */
    private File findJdkHome() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File jh = new File(javaHome);
            if (new File(jh, "jmods").exists()) {
                return jh;
            }
            if (jh.getParentFile() != null && new File(jh.getParentFile(), "jmods").exists()) {
                return jh.getParentFile();
            }
        }
        return null;
    }

    /**
     * 判断是否为 app-image 类型。
     */
    private boolean isAppImage() {
        return "app-image".equals(type);
    }

    // ==================== 平台特定命令配置 ====================

    /**
     * 追加 Windows 平台专属的 jpackage 命令选项。
     */
    private void appendWindowsOptions(List<String> command) {
        if (!SystemUtils.IS_OS_WINDOWS) {
            return;
        }
        if (Boolean.TRUE.equals(winConsole) && !isAppImage()) {
            command.add("--win-console");
        }
        if (Boolean.TRUE.equals(winDirChooser) && !isAppImage()) {
            command.add("--win-dir-chooser");
        }
        addOptionIfPresent(command, "--win-help-url", winHelpUrl);
        if (Boolean.TRUE.equals(winMenu) && !isAppImage()) {
            command.add("--win-menu");
        }
        addOptionIfPresent(command, "--win-menu-group", winMenuGroup);
        if (Boolean.TRUE.equals(winPerUserInstall) && !isAppImage()) {
            command.add("--win-per-user-install");
        }
        if (Boolean.TRUE.equals(winShortcut) && !isAppImage()) {
            command.add("--win-shortcut");
        }
        if (Boolean.TRUE.equals(winShortcutPrompt) && !isAppImage()) {
            command.add("--win-shortcut-prompt");
        }
        addOptionIfPresent(command, "--win-update-url", winUpdateUrl);
        if (StringUtils.isNotEmpty(winUpgradeUuid) && !isAppImage()) {
            command.add("--win-upgrade-uuid");
            command.add(winUpgradeUuid);
        }
    }

    /**
     * 追加 macOS 平台专属的 jpackage 命令选项。
     */
    private void appendMacOptions(List<String> command) {
        if (!SystemUtils.IS_OS_MAC) {
            return;
        }
        addOptionIfPresent(command, "--mac-package-identifier", macPackageIdentifier);
        addOptionIfPresent(command, "--mac-package-name", macPackageName);
        addOptionIfPresent(command, "--mac-package-signing-prefix", macPackageSigningPrefix);
        addOptionIfPresent(command, "--mac-sign", macSign);
        addOptionIfPresent(command, "--mac-signing-keychain", macSigningKeychain);
        addOptionIfPresent(command, "--mac-signing-key-user-name", macSigningKeyUserName);
        if (Boolean.TRUE.equals(macAppStore)) {
            command.add("--mac-app-store");
        }
        addFileOption(command, "--mac-entitlements", macEntitlements, "mac-entitlements 文件不存在: ");
        addOptionIfPresent(command, "--mac-app-category", macAppCategory);
    }

    /**
     * 追加 Linux 平台专属的 jpackage 命令选项。
     */
    private void appendLinuxOptions(List<String> command) {
        if (!SystemUtils.IS_OS_LINUX) {
            return;
        }
        addOptionIfPresent(command, "--linux-package-name", linuxPackageName);
        addOptionIfPresent(command, "--linux-deb-maintainer", linuxDebMaintainer);
        addOptionIfPresent(command, "--linux-menu-group", linuxMenuGroup);
        if (Boolean.TRUE.equals(linuxShortcut)) {
            command.add("--linux-shortcut");
        }
    }

    // ==================== 命令构建辅助方法 ====================

    /**
     * 当值非空时，向命令列表添加一个选项及其值。
     */
    private void addOptionIfPresent(List<String> command, String option, String value) {
        if (StringUtils.isNotEmpty(value)) {
            command.add(option);
            command.add(value);
        }
    }

    /**
     * 当值非空时，向命令列表添加一个选项及其带引号的值。
     */
    private void addQuotedOptionIfPresent(List<String> command, String option, String value) {
        if (StringUtils.isNotEmpty(value)) {
            addQuotedOption(command, option, value);
        }
    }

    /**
     * 向命令列表添加一个选项及其带引号的值（不检查空值）。
     */
    private void addQuotedOption(List<String> command, String option, String value) {
        command.add(option);
        command.add("\"" + value + "\"");
    }

    /**
     * 向命令列表重复添加选项，为列表中的每个元素生成一对 {@code option value}。
     */
    private void addRepeatedOption(List<String> command, String option, List<String> values) {
        if (values != null && !values.isEmpty()) {
            for (String value : values) {
                command.add(option);
                command.add(value);
            }
        }
    }

    /**
     * 向命令列表重复添加带引号值的选项。
     */
    private void addRepeatedQuotedOption(List<String> command, String option, List<String> values) {
        if (values != null && !values.isEmpty()) {
            for (String value : values) {
                command.add(option);
                command.add("\"" + value + "\"");
            }
        }
    }

    /**
     * 添加需要文件路径校验的选项。若路径无效则输出警告日志。
     */
    private void addFileOption(List<String> command, String option, String filePath, String warnPrefix) {
        if (StringUtils.isEmpty(filePath)) {
            return;
        }
        File resolved = path(filePath);
        if (resolved != null) {
            command.add(option);
            command.add("\"" + resolved.getAbsolutePath() + "\"");
        } else {
            getLog().warn(warnPrefix + filePath);
        }
    }

    // ==================== 文件与目录操作 ====================

    /**
     * 解析文件路径：先尝试绝对路径，再尝试相对于项目根目录的路径。
     *
     * @param path 文件路径（绝对或相对）
     * @return 找到的文件对象；如果路径无效则返回 {@code null}
     */
    public File path(String path) {
        File result = new File(path);
        if (result.exists()) {
            return result;
        }
        result = new File(project.getBasedir(), path);
        if (result.exists()) {
            return result;
        }
        return null;
    }

    /**
     * 复制项目所有运行时依赖和主 JAR 到工作目录的 {@code lib/} 子目录。
     *
     * @throws MojoExecutionException 如果文件复制或目录创建失败
     */
    private void copyLibrary() throws MojoExecutionException {
        File lib = mkdir(workDirectory, CommonConstant.LIB);

        String buildDir = project.getBuild().getDirectory();
        String finalName = project.getBuild().getFinalName() + "." + project.getPackaging();

        // 复制所有运行时依赖
        for (Artifact artifact : project.getArtifacts()) {
            File target = new File(lib, artifact.getFile().getName());
            getLog().info("正在复制文件: [" + artifact.getFile().getName() + "] 至 [" + target.getAbsolutePath() + "]");
            FileUtils.copy(artifact.getFile(), target);
        }

        // 复制主 JAR
        File source = new File(buildDir, finalName);
        File target = new File(lib, finalName);
        getLog().info("正在复制文件: [" + finalName + "] 至 [" + target.getAbsolutePath() + "]");
        FileUtils.copy(source, target);

        // jpackage 要求 --main-jar 使用相对于 --input 目录的路径（不能使用绝对路径）
        this.mainJar = target.getName();
    }

    /**
     * 打包完成后清理临时 lib 目录。
     */
    private void cleanLib() {
        if (workDirectory == null) {
            return;
        }
        File lib = new File(workDirectory, CommonConstant.LIB);
        if (lib.exists()) {
            try {
                FileUtils.remove(lib);
                getLog().info("已清理 lib 目录: " + lib.getAbsolutePath());
            } catch (MojoExecutionException e) {
                getLog().warn("清理 lib 目录失败: " + e.getMessage());
            }
        }
    }

    /**
     * 清理构建输出目录中已有的 JavaFX 相关文件。
     *
     * @throws MojoExecutionException 如果删除操作失败
     */
    private void clear() throws MojoExecutionException {
        String buildDir = project.getBuild().getDirectory();
        FileUtils.remove(new File(buildDir, CommonConstant.JAVAFX));
    }

    /**
     * 在构建输出目录下创建 JavaFX 工作目录。
     *
     * @return 工作目录的绝对路径
     * @throws MojoExecutionException 如果目录创建失败
     */
    private String createWorkDirectory() throws MojoExecutionException {
        String buildDir = project.getBuild().getDirectory();
        return mkdir(buildDir, CommonConstant.JAVAFX).getAbsolutePath();
    }

    /**
     * 创建指定路径的目录（如果不存在）。
     *
     * @param parentDir 父目录路径
     * @param childName 子目录名称
     * @return 创建的目录文件对象
     * @throws MojoExecutionException 如果目录创建失败
     */
    private File mkdir(String parentDir, String childName) throws MojoExecutionException {
        File dir = new File(parentDir, childName);
        if (!dir.exists()) {
            getLog().info("开始创建目录: [" + dir.getAbsolutePath() + "]");
            if (!dir.mkdirs()) {
                throw new MojoExecutionException("创建目录失败: [" + dir.getAbsolutePath() + "]");
            }
        }
        return dir;
    }
}
