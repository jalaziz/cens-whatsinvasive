package edu.ucla.cens.whatsinvasive.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Compress {
    
    public static void compress(String path, String outFile) throws IOException {
        File file = new File(path);
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outFile));
        
        if(file.isDirectory()) {
            compressDirectory(file.getName(), zos, file.getParent());
        } else {
            compressFile(file.getName(), zos, file.getParent());
        }
        
        zos.close();
    }
    
    private static void compressDirectory(String dirpath, ZipOutputStream zos, String src) throws IOException {
        File dir = new File(src, dirpath);

        for(String entry : dir.list()) {
            File file = new File(dir, entry);
            
            String path = dirpath + File.separator + entry;
            
            if(file.isDirectory()) {
                compressDirectory(path, zos, src);
            } else {
                compressFile(path, zos, src);
            }
        }
    }
    
    private static void compressFile(String filename, ZipOutputStream zos, String src) throws IOException {
        File file = new File(src, filename);
        FileInputStream fis = new FileInputStream(file);
        
        ZipEntry ze = new ZipEntry(filename);
        zos.putNextEntry(ze);
        
        int bytesRead;
        byte[] buffer = new byte[4096];
        
        while((bytesRead = fis.read(buffer)) != -1)
            zos.write(buffer, 0, bytesRead);
        fis.close();
    }

}
