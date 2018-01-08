/*
 * Copyright 2014-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.quancheng.saluki.oauth2.zuul.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.github.os72.protocjar.Protoc;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.InvalidProtocolBufferException;
import com.quancheng.saluki.oauth2.common.BDException;
import com.quancheng.saluki.oauth2.zuul.service.ProtobufFileService;

/**
 * @author liushiming
 * @version ProtobufFileServiceImpl.java, v 0.0.1 2018年1月8日 下午4:16:18 liushiming
 */
public class ProtobufFileServiceImpl implements ProtobufFileService {

  private static final String PROTOBUF_FILE_DIRECYTORY = "/var/protos/";

  @Override
  public byte[] protobufService(InputStream serviceStream, String protoServiceFileName) {
    try {
      String includePath = this.uploadZipFile(serviceStream, protoServiceFileName);
      Path descriptorPath = Files.createTempFile("descriptor", ".pb.bin");
      ImmutableList.Builder<String> builder = ImmutableList.<String>builder()//
          .add("--include_std_types")//
          .add("-I" + includePath)
          .add("--descriptor_set_out=" + descriptorPath.toAbsolutePath().toString())//
      ;
      List<String> protoServiceFilePath = Lists.newArrayList();
      Files.walkFileTree(Paths.get(includePath), new FileVisitor<Path>() {

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
            throws IOException {
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          if (file.getFileName().startsWith(protoServiceFileName)) {
            protoServiceFilePath.add(file.toFile().getAbsolutePath());
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          return FileVisitResult.CONTINUE;
        }

      });
      ImmutableList<String> protocArgs = builder.add(protoServiceFilePath.get(0)).build();
      int status = Protoc.runProtoc(protocArgs.toArray(new String[0]));
      if (status != 0) {
        throw new IllegalArgumentException(
            String.format("Got exit code [%d] from protoc with args [%s]", status, protocArgs));
      }
      return Files.readAllBytes(descriptorPath);
    } catch (IOException | InterruptedException e) {
      throw new BDException(e.getMessage(), e);
    }
  }

  @Override
  public byte[] protobufInputOutput(InputStream inputStream, InputStream outputStream) {
    return null;
  }

  private String uploadZipFile(InputStream serviceStream, String serviceName) throws IOException {
    String unZipRealPath = PROTOBUF_FILE_DIRECYTORY + serviceName;
    File unZipFile = new File(unZipRealPath);
    if (!unZipFile.exists()) {
      unZipFile.mkdirs();
    }
    ZipInputStream zipInputStream = null;
    try {
      zipInputStream = new ZipInputStream(serviceStream);
      ZipEntry zipEntry;
      while ((zipEntry = zipInputStream.getNextEntry()) != null) {
        String zipEntryName = zipEntry.getName();
        String outPath = unZipRealPath + "/" + zipEntryName;
        File file = new File(outPath.substring(0, outPath.lastIndexOf('/')));
        if (!file.exists()) {
          file.mkdirs();
        }
        if (new File(outPath).isDirectory()) {
          continue;
        }
        OutputStream outputStream = new FileOutputStream(outPath);
        byte[] bytes = new byte[4096];
        int len;
        while ((len = zipInputStream.read(bytes)) > 0) {
          outputStream.write(bytes, 0, len);
        }
        outputStream.close();
        zipInputStream.closeEntry();
      }
    } catch (IOException e) {
      throw e;
    } finally {
      try {
        if (zipInputStream != null)
          zipInputStream.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return unZipRealPath;
  }

  public static void main(String[] args) {
    String file =
        "/Users/liushiming/project/java/saluki/saluki-example/saluki-example-api/src/main/example.zip";
    ProtobufFileServiceImpl service = new ProtobufFileServiceImpl();
    try {
      byte[] protobytes = service.protobufService(new FileInputStream(file), "hello_service.proto");
      System.out.println(FileDescriptorSet.parseFrom(protobytes));
    } catch (FileNotFoundException | InvalidProtocolBufferException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }


  }

}
