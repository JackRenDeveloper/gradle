/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.nativeintegration.filesystem;

import com.google.common.collect.ImmutableMap;
import net.rubygrapefruit.platform.file.Files;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.nativeintegration.filesystem.jdk7.Jdk7FileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.services.FallbackFileMetadataAccessor;
import org.gradle.internal.nativeintegration.filesystem.services.NativePlatformBackedFileMetadataAccessor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.UUID;

@Threads(2)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class FileMetadataAccessorBenchmark {
    private static final Map<String, FileMetadataAccessor> ACCESSORS = ImmutableMap.<String, FileMetadataAccessor>builder()
        .put(FallbackFileMetadataAccessor.class.getSimpleName(), new FallbackFileMetadataAccessor())
        .put(NativePlatformBackedFileMetadataAccessor.class.getSimpleName(), new NativePlatformBackedFileMetadataAccessor(net.rubygrapefruit.platform.Native.get(Files.class)))
        .put(Jdk7FileMetadataAccessor.class.getSimpleName(), new Jdk7FileMetadataAccessor())
        .put(NioFileMetadataAccessor.class.getSimpleName(), new NioFileMetadataAccessor())
        .build();


    @Param({
        "FallbackFileMetadataAccessor",
        "NativePlatformBackedFileMetadataAccessor",
        "Jdk7FileMetadataAccessor",
        "NioFileMetadataAccessor"
    })
    String accessorClassName;

    FileMetadataAccessor accessor;
    File missing;
    Path missingPath;
    File directory;
    Path directoryPath;
    File realFile;
    Path realFilePath;

    @Setup
    public void prepare() throws IOException {
        accessor = getAccessor(accessorClassName);
        missing = new File(UUID.randomUUID().toString());
        missingPath = missing.toPath();
        directory = File.createTempFile("jmh", "dir");
        directoryPath = directory.toPath();
        directory.mkdirs();
        realFile = File.createTempFile("jmh", "tmp");
        realFilePath = realFile.toPath();

        FileOutputStream fos = new FileOutputStream(realFile);
        fos.write(new byte[1024]);
        fos.close();
    }

    @TearDown
    public void tearDown() {
        directory.delete();
        realFile.delete();
    }

    @SuppressWarnings("unchecked")
    private static FileMetadataAccessor getAccessor(String name) {
        return ACCESSORS.get(name);
    }

    @Benchmark
    public void stat_missing_file(Blackhole bh) throws IOException {
        bh.consume(getAccessor(accessorClassName).stat(missingPath));
    }

    @Benchmark
    public void stat_directory(Blackhole bh) throws IOException {
        bh.consume(getAccessor(accessorClassName).stat(directoryPath));
    }

    @Benchmark
    public void stat_existing(Blackhole bh) throws IOException {
        bh.consume(getAccessor(accessorClassName).stat(realFilePath));
    }

    private static class NioFileMetadataAccessor implements FileMetadataAccessor {

        NioFileMetadataAccessor() {
        }

        @Override
        public FileMetadataSnapshot stat(File f) {
            try {
                BasicFileAttributes bfa = java.nio.file.Files.readAttributes(f.toPath(), BasicFileAttributes.class);
                if (bfa.isDirectory()) {
                    return DefaultFileMetadata.directory();
                }
                return new DefaultFileMetadata(FileType.RegularFile, bfa.lastModifiedTime().toMillis(), bfa.size());
            } catch (IOException e) {
                return DefaultFileMetadata.missing();
            }
        }

        @Override
        public FileMetadataSnapshot stat(Path path) throws IOException {
            try {
                BasicFileAttributes bfa = java.nio.file.Files.readAttributes(path, BasicFileAttributes.class);
                if (bfa.isDirectory()) {
                    return DefaultFileMetadata.directory();
                }
                return new DefaultFileMetadata(FileType.RegularFile, bfa.lastModifiedTime().toMillis(), bfa.size());
            } catch (IOException e) {
                return DefaultFileMetadata.missing();
            }
        }
    }
}
