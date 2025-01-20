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

/**
 * @author lxwise
 * @create 2024-09
 * @description: 文件工具类
 * @version: 1.0
 * @email: lstart980@gmail.com
 */
public class FileUtils {

    /**
     * 递归删除
     *
     * @param image 形象
     * @throws MojoExecutionException mojo执行异常
     */
    public static void remove(File image) throws MojoExecutionException {
        if (image.exists()) {
            try {
                Files.walk(image.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                throw new MojoExecutionException("文件删除失败 " + image.getAbsolutePath(), e);
            }
        }
    }

    /**
     * 复制
     *
     * @param source 源
     * @param target
     * @throws MojoExecutionException Mojo 执行异常
     */
    public static void copy(File source, File target) throws MojoExecutionException {
        try (InputStream input = Files.newInputStream(source.toPath()); OutputStream output = Files.newOutputStream(target.toPath());) {
            IOUtils.copy(input, output);
        } catch (Exception e) {
            throw new MojoExecutionException("文件复制失败,源文件:[" + source.getAbsolutePath() + "],目标文件:[" + target.getAbsolutePath() + "]", e);
        }
    }
}
