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
import java.util.Set;

/**
 * @author lxwise
 * @create 2024-09
 * @description: 打包核心类
 * @version: 1.0
 * @email: lstart980@gmail.com
 */
@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.PACKAGE)
public class JPackageMojo extends AbstractMojo {

    /**
     * 项目
     */
    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    /**
     * 工作目录
     */
    private String workDirectory;
    /**
     * 资源目录
     */
    private String resourceDirectory;

    /**
     * 运行文件入口类
     */
    @Parameter(required = true)
    private String mainClass;

    /**
     * 主jar
     */
    private String mainJar;
    /**
     * image 路径
     */
    @Parameter
    private String imagePath;

    /**
     * 传递给｛@code可执行文件｝的vm选项列表。
     */
    @Parameter
    private List<String> options;

    /**
     * 为执行的程序用空格分隔的参数。数组
     */
    @Parameter
    private List<String> arguments;

    /**
     * 类型
     * {"app-image", "exe", "msi", "rpm", "deb", "pkg", "dmg"}
     */
    @Parameter
    private String type;
    /**
     * 版本
     */
    @Parameter(defaultValue = "${project.version}")
    private String version;
    /**
     * 版权
     */
    @Parameter
    private String copyright;

    /**
     * 描述
     */
    @Parameter
    private String description;

    /**
     * 图标
     */
    @Parameter
    private String icon;

    /**
     * 应用名称
     */
    @Parameter
    private String name;
    /**
     * 厂商
     */
    @Parameter
    private String vendor;
    /**
     * 启用详细输出
     */
    @Parameter(defaultValue = "false")
    private Boolean verbose;

    /**
     * MAC 软件包标识符
     */
    @Parameter
    private String macPackageIdentifier;
    /**
     * MAC 软件包名称
     * 显示在菜单栏中的应用程序的名称,可能与应用程序名称不同。此名称的长度必须小于16个字符，并且适合在菜单栏和应用程序信息窗口中显示。默认为应用程序名称。
     */
    @Parameter
    private String macPackageName;
    /**
     * MAC 软件包签名前缀
     */
    @Parameter
    private String macPackageSigningPrefix;
    /**
     * MAC 标志
     */
    @Parameter
    private String macSign;
    /**
     * Mac 签名钥匙串
     */
    @Parameter
    private String macSigningKeychain;
    /**
     * Mac 签名钥匙串
     */
    @Parameter
    private String macSigningKeyUserName;
    /**
     * Mac 应用商店
     */
    @Parameter(defaultValue = "false")
    private Boolean macAppStore;
    /**
     * Mac 权利
     */
    @Parameter
    private String macEntitlements;
    /**
     * Mac 应用类别
     */
    @Parameter
    private String macAppCategory;

    /**
     * 关于网址
     */
    @Parameter
    private String aboutUrl;

    /**
     * 目录的绝对路径
     */
    @Parameter
    private String installDir;

    /**
     * 为应用程序创建控制台启动器，应指定需要控制台交互的应用程序
     */
    @Parameter(defaultValue = "false")
    private Boolean winConsole;

    /**
     * 添加对话框，以便用户选择安装产品的目录
     */
    @Parameter(defaultValue = "false")
    private Boolean winDirChooser;

    /**
     * 用户可以获取更多信息或技术支持的URL
     */
    @Parameter
    private String winHelpUrl;

    /**
     * 请求为此应用程序添加开始菜单快捷方式
     */
    @Parameter(defaultValue = "false")
    private Boolean winMenu;

    /**
     * 将此应用程序放置在的开始菜单组
     */
    @Parameter
    private String winMenuGroup;

    /**
     * 请求按用户安装
     */
    @Parameter(defaultValue = "false")
    private Boolean winPerUserInstall;

    /**
     * 请求为此应用程序创建桌面快捷方式
     */
    @Parameter(defaultValue = "false")
    private Boolean winShortcut;

    /**
     * 添加对话框，以便用户选择是否由安装程序创建快捷方式
     */
    @Parameter(defaultValue = "false")
    private Boolean winShortcutPrompt;

    /**
     * 用应用程序更新信息的URL
     */
    @Parameter
    private String winUpdateUrl;

    /**
     * 与此软件包升级相关联的UUID
     */
    @Parameter
    private String winUpgradeUuid;
    /**
     * Linux软件包的名称,默认为应用程序名称
     */
    @Parameter
    private String linuxPackageName;
    /**
     * .deb包的维护者 email-address
     */
    @Parameter
    private String linuxDebMaintainer;
    /**
     * 将此应用程序放置在的菜单组
     */
    @Parameter
    private String linuxMenuGroup;
    /**
     * 为应用程序创建快捷方式
     */
    @Parameter(defaultValue = "false")
    private Boolean linuxShortcut;

    @Override
    public void execute() throws MojoExecutionException {
        // 日志记录，表明打包任务开始执行
        getLog().info("开始执行jfx:package...");
        // 初始化工作目录、资源目录等相关内容
        init();
        // 创建一个执行器来运行命令
        DefaultExecutor executor = new DefaultExecutor();
        // 构建打包所需的命令
        CommandLine command = buildCommand();
        try {
            // 执行命令
            executor.execute(command);
        } catch (IOException e) {
            // 如果执行失败，则抛出异常并记录错误信息
            throw new MojoExecutionException("指令执行失败", e);
        }
    }

    /**
     * 初始化工作目录、资源目录等相关内容
     * @throws MojoExecutionException
     */
    private void init() throws MojoExecutionException {
        // 清理构建目录中已有的JavaFX相关内容
        clear();
        // 初始化工作目录路径
        this.workDirectory = workDirectory();
        // 定位项目根目录下的JavaFX资源文件夹
        File resource = new File(project.getBasedir(), CommonConstant.JAVAFX);
        // 初始化资源目录，根据操作系统选择适配的资源路径
        initResourceDir(resource);
        // 如果镜像路径为空，则复制依赖库到工作目录
        if (StringUtils.isEmpty(imagePath)) {
            copyLibrary();
        }
    }

    /**
     * 初始化资源目录，根据操作系统选择适配的资源路径
     * @param resource
     */
    private void initResourceDir(File resource) {
        if (resource.exists()) {
            if (SystemUtils.IS_OS_WINDOWS) {
                // Windows操作系统，初始化Windows资源目录
                File windows = new File(resource, CommonConstant.WINDOWS);
                if (windows.exists()) {
                    resourceDirectory = windows.getAbsolutePath();
                }
            } else if (SystemUtils.IS_OS_MAC) {
                // macOS操作系统，初始化Mac资源目录
                File mac = new File(resource, CommonConstant.MAC);
                if (mac.exists()) {
                    resourceDirectory = mac.getAbsolutePath();
                }
            } else if (SystemUtils.IS_OS_LINUX) {
                // Linux操作系统，初始化Linux资源目录
                File linux = new File(resource, CommonConstant.LINUX);
                if (linux.exists()) {
                    resourceDirectory = linux.getAbsolutePath();
                }
            }
        }
    }

    /**
     * 获取项目根目录下的文件
     * @param path
     * @return
     */
    public File path(String path) {
        // 首先尝试直接定位路径文件
        File result = new File(path);
        if (result.exists()) {
            return result;
        }
        // 如果路径无效，尝试以项目根目录为基准定位文件
        result = new File(project.getBasedir(), path);
        if (result.exists()) {
            return result;
        }
        // 如果都不存在，返回null
        return null;
    }

    /**
     * 构建打包所需的命令
     * @return
     */
    private CommandLine buildCommand() {
        List<String> command = new ArrayList<>();
        // 添加jpackage命令
        command.add("jpackage");
        command.add("--dest");
        command.add("\"" + workDirectory + "\"");
        // 如果指定了打包类型，则添加相关选项
        if (StringUtils.isNotEmpty(type)) {
            command.add("--type");
            command.add(type);
        }
        // 添加应用版本号
        command.add("--app-version");
        command.add(version);
        // 如果指定版权，则添加相关选项
        if (StringUtils.isNotEmpty(copyright)) {
            command.add("--copyright");
            command.add("\""+copyright+"\"");
        }
        // 添加应用描述
        if (StringUtils.isNotEmpty(description)) {
            command.add("--description");
            command.add(description);
        }
        // 添加应用图标
        if (StringUtils.isNotEmpty(icon)) {
            File path = path(icon);
            if (path != null) {
                command.add("--icon");
                command.add("\"" + path.getAbsolutePath() + "\"");
            } else {
                getLog().warn("icon文件不存在:" + icon);
            }
        }
        // 添加应用名称
        if (StringUtils.isEmpty(name)) {
            name = project.getName();
        }
        command.add("--name");
        command.add("\""+ name +"\"");
        // 添加应用供应商
        if (StringUtils.isNotEmpty(vendor)) {
            command.add("--vendor");
            command.add(vendor);
        }
        //启用详细输出
        if (verbose) {
            command.add("--verbose");
        }
        // 添加关于链接
        if (StringUtils.isNotEmpty(aboutUrl)) {

            command.add("--about-url");
            command.add(aboutUrl);
        }
        // 安装目录
        if (StringUtils.isNotEmpty(installDir)) {
            command.add("--install-dir");
            command.add("\"" + installDir + "\"");
        }
        // 资源目录
        if (StringUtils.isNotEmpty(resourceDirectory)) {
            command.add("--resource-dir");
            command.add("\"" + resourceDirectory + "\"");
        }
        // 镜像路径
        if(StringUtils.isEmpty(imagePath)){
            command.add("--input");
            command.add("\"" + new File(workDirectory, CommonConstant.LIB).getAbsolutePath() + "\"");
            command.add("--main-class");
            command.add(mainClass);
            command.add("--main-jar");
            command.add("\"" + mainJar + "\"");
        }else{
            File image=path(imagePath);
            command.add("--runtime-image");
            command.add("\"" + image.getAbsolutePath() + "\"");
            command.add("--module");
            command.add(mainClass);
        }
        if (StringUtils.isEmpty(imagePath) && arguments != null && !arguments.isEmpty()) {
            for (String arg : arguments) {
                command.add("--arguments");
                command.add(arg);
            }
        }
        if (StringUtils.isEmpty(imagePath) && options != null && !options.isEmpty()) {
            options.forEach(option -> {
                command.add("--java-options");
                command.add("\"" + option + "\"");
            });
        }
        windows(command);
        mac(command);
        linux(command);
        getLog().info("执行指令:" + String.join(" ", command));
        return CommandLine.parse(String.join(" ", command));
    }

    /**
     * 配置 Linux 平台的命令选项
     *
     * @param command 要修改的命令列表
     */
    private void linux(List<String> command) {
        // 检查当前系统是否是 Linux 系统
        if (!SystemUtils.IS_OS_LINUX) {
            return; // 如果不是 Linux 系统，直接返回
        }
        // 如果 Linux软件包的名称 非空，添加相应的选项
        if (StringUtils.isNotEmpty(linuxPackageName)) {
            command.add("--linux-package-name");
            command.add(linuxPackageName);
        }
        // 如果 deb包的维护者 非空，添加相应的选项
        if (StringUtils.isNotEmpty(linuxDebMaintainer)) {
            command.add("--linux-deb-maintainer");
            command.add(linuxDebMaintainer);
        }
        // 如果 菜单组 非空，添加相应的选项
        if (StringUtils.isNotEmpty(linuxMenuGroup)) {
            command.add("--linux-menu-group");
            command.add(linuxMenuGroup);
        }
        // 如果 快捷方式 为 true，添加相应的选项
        if (linuxShortcut) {
            command.add("--linux-shortcut");
        }
    }

    /**
     * 配置 Windows 平台的命令选项
     *
     * @param command 要修改的命令列表
     */
    private void windows(List<String> command) {
        // 检查当前系统是否是 Windows 系统
        if (!SystemUtils.IS_OS_WINDOWS) {
            return; // 如果不是 Windows 系统，直接返回
        }
        // 如果 winConsole 为 true，添加相应的选项
        if (winConsole) {
            command.add("--win-console");
        }
        // 如果 winDirChooser 为 true，添加相应的选项
        if (winDirChooser) {
            command.add("--win-dir-chooser");
        }
        // 如果 winHelpUrl 非空，添加相应的选项
        if (StringUtils.isNotEmpty(winHelpUrl)) {
            command.add("--win-help-url");
            command.add(winHelpUrl);
        }
        // 如果 winMenu 为 true，添加相应的选项
        if (winMenu) {
            command.add("--win-menu");
        }
        // 如果 winMenuGroup 非空，添加相应的选项
        if (StringUtils.isNotEmpty(winMenuGroup)) {
            command.add("--win-menu-group");
            command.add(winMenuGroup);
        }
        // 如果 winPerUserInstall 为 true，添加相应的选项
        if (winPerUserInstall) {
            command.add("--win-per-user-install");
        }
        // 如果 winShortcut 为 true，添加相应的选项
        if (winShortcut) {
            command.add("--win-shortcut");
        }
        // 如果 winShortcutPrompt 为 true，添加相应的选项
        if (winShortcutPrompt) {
            command.add("--win-shortcut-prompt");
        }
        // 如果 winUpdateUrl 非空，添加相应的选项
        if (StringUtils.isNotEmpty(winUpdateUrl)) {
            command.add("--win-update-url");
            command.add(winUpdateUrl);
        }
        // 如果 winUpgradeUuid 非空，添加相应的选项
        if (StringUtils.isNotEmpty(winUpgradeUuid)) {
            command.add("--win-upgrade-uuid");
            command.add(winUpgradeUuid);
        }
    }

    /**
     * 配置 macOS 平台的命令选项
     *
     * @param command 要修改的命令列表
     */
    private void mac(List<String> command) {
        // 检查当前系统是否是 macOS 系统
        if (!SystemUtils.IS_OS_MAC) {
            return; // 如果不是 macOS 系统，直接返回
        }
        // 如果 macPackageIdentifier 非空，添加相应的选项
        if (StringUtils.isNotEmpty(macPackageIdentifier)) {
            command.add("--mac-package-identifier");
            command.add(macPackageIdentifier);
        }
        // 如果 macPackageName 非空，添加相应的选项
        if (StringUtils.isNotEmpty(macPackageName)) {
            command.add("--mac-package-name");
            command.add(macPackageName);
        }
        // 如果 macPackageSigningPrefix 非空，添加相应的选项
        if (StringUtils.isNotEmpty(macPackageSigningPrefix)) {
            command.add("--mac-package-signing-prefix");
            command.add(macPackageSigningPrefix);
        }
        // 如果 macSign 非空，添加相应的选项
        if (StringUtils.isNotEmpty(macSign)) {
            command.add("--mac-sign");
            command.add(macSign);
        }
        // 如果 macSigningKeychain 非空，添加相应的选项
        if (StringUtils.isNotEmpty(macSigningKeychain)) {
            command.add("--mac-signing-keychain");
            command.add(macSigningKeychain);
        }
        // 如果 macSigningKeyUserName 非空，添加相应的选项
        if (StringUtils.isNotEmpty(macSigningKeyUserName)) {
            command.add("--mac-signing-key-user-name");
            command.add(macSigningKeyUserName);
        }
        // 如果 macAppStore 为 true，添加相应的选项
        if (macAppStore) {
            command.add("--mac-app-store");
        }
        // 如果 macEntitlements 非空，检查路径并添加选项
        if (StringUtils.isNotEmpty(macEntitlements)) {
            File path = path(macEntitlements);
            if (path != null) {
                command.add("--mac-entitlements");
                command.add("\"" + path.getAbsolutePath() + "\"");
            } else {
                getLog().warn("mac-entitlements 文件不存在"); // 打印警告日志
            }
        }
        // 如果 macAppCategory 非空，添加相应的选项
        if (StringUtils.isNotEmpty(macAppCategory)) {
            command.add("--mac-app-category");
            command.add(macAppCategory);
        }
    }


    /**
     * 复制项目所需的依赖库到指定工作目录。
     *
     * @throws MojoExecutionException 如果文件复制失败或目录创建失败
     */
    private void copyLibrary() throws MojoExecutionException {
        // 创建用于存放依赖库的目录
        File lib = mkdir(workDirectory, CommonConstant.LIB);

        // 获取构建输出目录和最终生成的主 JAR 文件名称
        String directory = project.getBuild().getDirectory();
        String finalName = project.getBuild().getFinalName() + "." + project.getPackaging();

        // 遍历项目的所有依赖并复制到目标目录
        for (Artifact artifact : project.getArtifacts()) {
            File target = new File(lib, artifact.getFile().getName());
            getLog().info("正在复制文件:[" + artifact.getFile().getName() + "]至[" + target.getAbsolutePath() + "]");
            // 复制依赖文件到目标目录
            FileUtils.copy(artifact.getFile(), target);
        }

        // 复制项目的主 JAR 文件到目标目录
        File source = new File(directory, finalName);
        File target = new File(lib, finalName);
        getLog().info("正在复制文件:[" + finalName + "]至[" + target.getAbsolutePath() + "]");
        FileUtils.copy(source, target);

        // 记录主 JAR 文件的路径，供后续使用
        mainJar = target.getAbsolutePath();
    }

    /**
     * 清理构建目录中的 JavaFX 相关文件。
     *
     * @throws MojoExecutionException 如果删除操作失败
     */
    private void clear() throws MojoExecutionException {
        // 定位构建目录下的 JavaFX 文件夹
        String directory = project.getBuild().getDirectory();
        File file = new File(directory, CommonConstant.JAVAFX);

        // 删除 JavaFX 文件夹及其内容
        FileUtils.remove(file);
    }

    /**
     * 获取或创建工作目录的绝对路径。
     *
     * @return 工作目录的绝对路径
     * @throws MojoExecutionException 如果目录创建失败
     */
    public String workDirectory() throws MojoExecutionException {
        // 定位构建输出目录并创建 JavaFX 文件夹
        String directory = project.getBuild().getDirectory();
        File file = mkdir(directory, CommonConstant.JAVAFX);

        // 返回工作目录的绝对路径
        return file.getAbsolutePath();
    }

    /**
     * 创建指定路径的目录（如果不存在）。
     *
     * @param directory 父目录路径
     * @param filename  子目录名称
     * @return 创建的目录文件对象
     * @throws MojoExecutionException 如果目录创建失败
     */
    private File mkdir(String directory, String filename) throws MojoExecutionException {
        // 创建目标目录文件对象
        File file = new File(directory, filename);

        // 如果目录不存在，则尝试创建
        if (!file.exists()) {
            getLog().info("开始创建目录:[" + file.getAbsolutePath() + "]");
            if (!file.mkdirs()) {
                // 创建失败抛出异常
                throw new MojoExecutionException("创建目录:[" + file.getAbsolutePath() + "]失败");
            }
        }

        // 返回创建的目录文件对象
        return file;
    }

}
