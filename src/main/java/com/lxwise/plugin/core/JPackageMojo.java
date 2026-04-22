package com.lxwise.plugin.core;

import com.lxwise.plugin.constant.CommonConstant;
import com.lxwise.plugin.utils.FileUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX 应用打包 Maven 插件核心 Mojo。
 * <p>
 * 基于 JDK 内置的 {@code jpackage} 工具，将 JavaFX 应用程序打包为各平台原生安装包。
 * 支持 Windows（exe、msi）、macOS（pkg、dmg）和 Linux（rpm、deb）等格式。
 * </p>
 * <p>
 * 使用方式：在 Maven 中执行 {@code mvn jfx:package} 目标即可触发打包流程。
 * 插件会自动收集项目依赖、构建 jpackage 命令行参数并执行打包。
 * </p>
 *
 * @author lxwise
 * @version 1.0
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
     * <p>若指定此参数，将使用 {@code --runtime-image} 模式打包，跳过依赖复制步骤。</p>
     */
    @Parameter
    private String imagePath;

    /** 传递给应用程序 JVM 的选项列表，如 {@code -Xmx512m} */
    @Parameter
    private List<String> options;

    /** 传递给应用程序主类的命令行参数列表 */
    @Parameter
    private List<String> arguments;

    // ==================== 通用打包配置 ====================

    /**
     * 打包输出类型。
     * <p>可选值：{@code app-image}、{@code exe}、{@code msi}、{@code rpm}、{@code deb}、{@code pkg}、{@code dmg}</p>
     */
    @Parameter
    private String type;

    /** 应用程序版本号，默认使用 Maven 项目版本 */
    @Parameter(defaultValue = "${project.version}")
    private String version;

    /** 版权信息 */
    @Parameter
    private String copyright;

    /** 应用程序描述 */
    @Parameter
    private String description;

    /** 应用程序图标路径（支持绝对路径或相对于项目根目录的相对路径） */
    @Parameter
    private String icon;

    /** 应用程序名称，默认使用 Maven 项目名称 */
    @Parameter
    private String name;

    /** 应用程序厂商/供应商名称 */
    @Parameter
    private String vendor;

    /** 是否启用 jpackage 详细输出，默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean verbose;

    /** 应用程序相关信息的 URL */
    @Parameter
    private String aboutUrl;

    /** 安装目录的绝对路径 */
    @Parameter
    private String installDir;

    // ==================== macOS 专属配置 ====================

    /** macOS 软件包标识符（如 {@code com.example.app}） */
    @Parameter
    private String macPackageIdentifier;

    /**
     * macOS 菜单栏中显示的应用名称。
     * <p>长度必须小于 16 个字符，默认使用应用名称。</p>
     */
    @Parameter
    private String macPackageName;

    /** macOS 软件包签名前缀 */
    @Parameter
    private String macPackageSigningPrefix;

    /** macOS 签名标志 */
    @Parameter
    private String macSign;

    /** macOS 签名钥匙串路径 */
    @Parameter
    private String macSigningKeychain;

    /** macOS 签名密钥用户名 */
    @Parameter
    private String macSigningKeyUserName;

    /** 是否面向 Mac App Store 发布，默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean macAppStore;

    /** macOS 权利文件路径（.entitlements 文件） */
    @Parameter
    private String macEntitlements;

    /** macOS 应用类别（如 {@code public.app-category.developer-tools}） */
    @Parameter
    private String macAppCategory;

    // ==================== Windows 专属配置 ====================

    /** 是否创建控制台启动器（适用于需要控制台交互的应用），默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean winConsole;

    /** 安装时是否允许用户选择安装目录，默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean winDirChooser;

    /** 帮助/技术支持页面的 URL */
    @Parameter
    private String winHelpUrl;

    /** 是否添加开始菜单快捷方式，默认 {@code false} */
    @Parameter(defaultValue = "false")
    private Boolean winMenu;

    /** 开始菜单中的分组名称 */
    @Parameter
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
    @Parameter
    private String winUpdateUrl;

    /** 与 MSI 升级关联的 UUID */
    @Parameter
    private String winUpgradeUuid;

    // ==================== Linux 专属配置 ====================

    /** Linux 软件包名称，默认使用应用名称 */
    @Parameter
    private String linuxPackageName;

    /** .deb 软件包的维护者邮箱地址 */
    @Parameter
    private String linuxDebMaintainer;

    /** Linux 菜单组名称 */
    @Parameter
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

    // ==================== 执行入口 ====================

    /**
     * 插件执行入口方法。
     * <p>
     * 执行流程：
     * <ol>
     *     <li>初始化工作目录和资源目录</li>
     *     <li>复制项目依赖到工作目录（非 runtime-image 模式）</li>
     *     <li>构建 jpackage 命令行</li>
     *     <li>执行 jpackage 命令完成打包</li>
     * </ol>
     * </p>
     *
     * @throws MojoExecutionException 如果初始化或命令执行失败
     */
    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("开始执行 jfx:package...");
        init();

        CommandLine command = buildCommand();
        try {
            new DefaultExecutor().execute(command);
        } catch (IOException e) {
            throw new MojoExecutionException("jpackage 指令执行失败", e);
        }
    }

    // ==================== 初始化逻辑 ====================

    /**
     * 初始化打包环境：清理旧文件、创建工作目录、定位资源目录、复制依赖。
     *
     * @throws MojoExecutionException 如果任何初始化步骤失败
     */
    private void init() throws MojoExecutionException {
        clear();
        this.workDirectory = createWorkDirectory();
        initResourceDir();
        if (StringUtils.isEmpty(imagePath)) {
            copyLibrary();
        }
    }

    /**
     * 根据当前操作系统初始化平台资源目录。
     * <p>
     * 在项目根目录下查找 {@code javafx/} 目录，并根据当前操作系统选择对应的子目录
     * （{@code windows/}、{@code mac/} 或 {@code linux/}）作为资源目录。
     * </p>
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
        addOptionIfPresent(command, "--type", type);

        // 版本号
        command.add("--app-version");
        command.add(version);

        // 通用选项
        addQuotedOptionIfPresent(command, "--copyright", copyright);
        addOptionIfPresent(command, "--description", description);

        // 图标（需要路径校验）
        addFileOption(command, "--icon", icon, "icon 文件不存在: ");

        // 应用名称（默认使用项目名称）
        if (StringUtils.isEmpty(name)) {
            name = project.getName();
        }
        addQuotedOption(command, "--name", name);

        addOptionIfPresent(command, "--vendor", vendor);

        if (Boolean.TRUE.equals(verbose)) {
            command.add("--verbose");
        }

        addOptionIfPresent(command, "--about-url", aboutUrl);
        addQuotedOptionIfPresent(command, "--install-dir", installDir);
        addQuotedOptionIfPresent(command, "--resource-dir", resourceDirectory);

        // 输入模式：普通模式 vs runtime-image 模式
        if (StringUtils.isEmpty(imagePath)) {
            addQuotedOption(command, "--input", new File(workDirectory, CommonConstant.LIB).getAbsolutePath());
            command.add("--main-class");
            command.add(mainClass);
            addQuotedOption(command, "--main-jar", mainJar);
        } else {
            File image = path(imagePath);
            addQuotedOption(command, "--runtime-image", image.getAbsolutePath());
            command.add("--module");
            command.add(mainClass);
        }

        // 应用参数和 JVM 选项（仅普通模式）
        if (StringUtils.isEmpty(imagePath)) {
            addRepeatedOption(command, "--arguments", arguments);
            addRepeatedQuotedOption(command, "--java-options", options);
        }

        // 平台特定选项
        appendWindowsOptions(command);
        appendMacOptions(command);
        appendLinuxOptions(command);

        getLog().info("执行指令: " + String.join(" ", command));
        return CommandLine.parse(String.join(" ", command));
    }

    // ==================== 平台特定命令配置 ====================

    /**
     * 追加 Windows 平台专属的 jpackage 命令选项。
     *
     * @param command 命令参数列表
     */
    private void appendWindowsOptions(List<String> command) {
        if (!SystemUtils.IS_OS_WINDOWS) {
            return;
        }
        addFlagIfTrue(command, "--win-console", winConsole);
        addFlagIfTrue(command, "--win-dir-chooser", winDirChooser);
        addOptionIfPresent(command, "--win-help-url", winHelpUrl);
        addFlagIfTrue(command, "--win-menu", winMenu);
        addOptionIfPresent(command, "--win-menu-group", winMenuGroup);
        addFlagIfTrue(command, "--win-per-user-install", winPerUserInstall);
        addFlagIfTrue(command, "--win-shortcut", winShortcut);
        addFlagIfTrue(command, "--win-shortcut-prompt", winShortcutPrompt);
        addOptionIfPresent(command, "--win-update-url", winUpdateUrl);
        addOptionIfPresent(command, "--win-upgrade-uuid", winUpgradeUuid);
    }

    /**
     * 追加 macOS 平台专属的 jpackage 命令选项。
     *
     * @param command 命令参数列表
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
        addFlagIfTrue(command, "--mac-app-store", macAppStore);
        addFileOption(command, "--mac-entitlements", macEntitlements, "mac-entitlements 文件不存在");
        addOptionIfPresent(command, "--mac-app-category", macAppCategory);
    }

    /**
     * 追加 Linux 平台专属的 jpackage 命令选项。
     *
     * @param command 命令参数列表
     */
    private void appendLinuxOptions(List<String> command) {
        if (!SystemUtils.IS_OS_LINUX) {
            return;
        }
        addOptionIfPresent(command, "--linux-package-name", linuxPackageName);
        addOptionIfPresent(command, "--linux-deb-maintainer", linuxDebMaintainer);
        addOptionIfPresent(command, "--linux-menu-group", linuxMenuGroup);
        addFlagIfTrue(command, "--linux-shortcut", linuxShortcut);
    }

    // ==================== 命令构建辅助方法 ====================

    /**
     * 当值非空时，向命令列表添加一个选项及其值。
     *
     * @param command 命令参数列表
     * @param option  选项名称（如 {@code --type}）
     * @param value   选项值，为空时跳过
     */
    private void addOptionIfPresent(List<String> command, String option, String value) {
        if (StringUtils.isNotEmpty(value)) {
            command.add(option);
            command.add(value);
        }
    }

    /**
     * 当值非空时，向命令列表添加一个选项及其带引号的值。
     *
     * @param command 命令参数列表
     * @param option  选项名称
     * @param value   选项值，为空时跳过
     */
    private void addQuotedOptionIfPresent(List<String> command, String option, String value) {
        if (StringUtils.isNotEmpty(value)) {
            addQuotedOption(command, option, value);
        }
    }

    /**
     * 向命令列表添加一个选项及其带引号的值（不检查空值）。
     *
     * @param command 命令参数列表
     * @param option  选项名称
     * @param value   选项值
     */
    private void addQuotedOption(List<String> command, String option, String value) {
        command.add(option);
        command.add("\"" + value + "\"");
    }

    /**
     * 当布尔值为 {@code true} 时，向命令列表添加一个标志选项。
     *
     * @param command 命令参数列表
     * @param flag    标志名称（如 {@code --verbose}）
     * @param value   布尔值，为 {@code null} 或 {@code false} 时跳过
     */
    private void addFlagIfTrue(List<String> command, String flag, Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            command.add(flag);
        }
    }

    /**
     * 向命令列表重复添加选项，为列表中的每个元素生成一对 {@code option value}。
     *
     * @param command 命令参数列表
     * @param option  选项名称
     * @param values  值列表，为 {@code null} 或空时跳过
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
     *
     * @param command 命令参数列表
     * @param option  选项名称
     * @param values  值列表，为 {@code null} 或空时跳过
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
     * 添加需要文件路径校验的选项。若路径无法定位则输出警告日志。
     *
     * @param command    命令参数列表
     * @param option     选项名称
     * @param filePath   配置的文件路径
     * @param warnPrefix 文件不存在时的警告前缀
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

        this.mainJar = target.getAbsolutePath();
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
