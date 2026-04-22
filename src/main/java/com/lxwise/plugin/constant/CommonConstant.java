package com.lxwise.plugin.constant;

/**
 * 通用常量定义接口。
 * <p>
 * 定义了插件中使用的目录名称等字符串常量，包括平台标识和工作目录名称。
 * 所有常量默认为 {@code public static final}。
 * </p>
 *
 * @author lxwise
 * @version 1.0
 * @since 2024-09
 */
public interface CommonConstant {

    /** JavaFX 工作目录名称，用于构建输出和资源定位 */
    String JAVAFX = "javafx";

    /** Windows 平台资源子目录名称 */
    String WINDOWS = "windows";

    /** macOS 平台资源子目录名称 */
    String MAC = "mac";

    /** Linux 平台资源子目录名称 */
    String LINUX = "linux";

    /** 依赖库存放子目录名称 */
    String LIB = "lib";
}
