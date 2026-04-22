package com.lxwise.plugin.utils;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 文件操作工具类。
 * <p>
 * 提供文件复制、递归删除等常用文件操作，封装了底层 IO 异常并统一转换为
 * {@link MojoExecutionException}，便于在 Maven 插件生命周期中使用。
 * </p>
 *
 * @author lxwise
 * @version 1.0
 * @since 2024-09
 */
public final class FileUtils {

    private FileUtils() {
        // 私有构造器，防止实例化
    }

    /**
     * 递归删除指定文件或目录及其所有内容。
     * <p>
     * 如果目标文件不存在，则不执行任何操作。删除时按逆序遍历，确保子文件先于父目录被删除。
     * </p>
     *
     * @param file 要删除的文件或目录，允许不存在的路径
     * @throws MojoExecutionException 如果删除过程中发生 IO 异常
     */
    public static void remove(File file) throws MojoExecutionException {
        if (!file.exists()) {
            return;
        }
        try (Stream<Path> paths = Files.walk(file.toPath())) {
            paths.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new MojoExecutionException("文件删除失败: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * 复制文件内容到目标位置。
     *
     * @param source 源文件，必须存在且可读
     * @param target 目标文件，如果已存在则覆盖
     * @throws MojoExecutionException 如果文件复制过程中发生异常
     */
    public static void copy(File source, File target) throws MojoExecutionException {
        try (InputStream input = Files.newInputStream(source.toPath());
             OutputStream output = Files.newOutputStream(target.toPath())) {
            IOUtils.copy(input, output);
        } catch (Exception e) {
            throw new MojoExecutionException(
                    "文件复制失败, 源文件: [" + source.getAbsolutePath()
                            + "], 目标文件: [" + target.getAbsolutePath() + "]", e);
        }
    }
}
