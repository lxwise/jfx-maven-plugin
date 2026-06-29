package com.lxwise.plugin.utils;

import org.apache.commons.lang3.SystemUtils;

/**
 * 平台检测工具类。
 * <p>
 * 提供当前操作系统平台和 CPU 架构的检测方法，用于构建输出文件的命名和路径判断。
 * </p>
 *
 * @author lxwise
 * @version 1.1
 * @since 2024-09
 */
public final class PlatformUtils {

    private PlatformUtils() {
        // 私有构造器，防止实例化
    }

    /**
     * 获取当前操作系统平台标识。
     *
     * @return 平台标识字符串：{@code "windows"}、{@code "linux"}、{@code "darwin"} 或 {@code "unknown"}
     */
    public static String platform() {
        if (SystemUtils.IS_OS_WINDOWS) {
            return "windows";
        } else if (SystemUtils.IS_OS_LINUX) {
            return "linux";
        } else if (SystemUtils.IS_OS_MAC) {
            return "darwin";
        } else {
            return "unknown";
        }
    }

    /**
     * 获取当前 CPU 架构标识。
     *
     * @return 架构标识字符串：{@code "arm64"}、{@code "x86_64"} 或原始架构名称
     */
    public static String arch() {
        String arch = System.getProperty("os.arch", "unknown");
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "arm64";
        } else if (arch.contains("x86") || arch.contains("amd64")) {
            return "x86_64";
        }
        return arch;
    }
}
