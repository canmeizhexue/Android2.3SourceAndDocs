/*
 * Copyright (C) 2008 The Android Open Source Project
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

package dalvik.system;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.ZipFile;

/**1.jar或者apk文件列表，里面有classes.dex文件，
		2.加载本地库
		3.加载类
		4.加载资源
 * Provides a simple {@link ClassLoader} implementation that operates on a
 * list of jar/apk files with classes.dex entries.  The directory that
 * holds the optimized form of the files is specified explicitly.  This
 * can be used to execute code not installed as part of an application.
 *
 * The best place to put the optimized DEX files is in app-specific
 * storage, so that removal of the app will automatically remove the
 * optimized DEX files.  If other storage is used (e.g. /sdcard), the
 * app may not have an opportunity to remove them.
 */
public class DexClassLoader extends ClassLoader {

    private static final boolean VERBOSE_DEBUG = false;

    /* constructor args, held for init */
    private final String mRawDexPath;//可能带有路径分隔符
    private final String mRawLibPath;//可能带有路径分隔符
    private final String mDexOutputPath;//保存优化过后的dex文件

    /*三个数组一样长，同下标的元素共同作用，
     * Parallel arrays for jar/apk files.
     *
     * (could stuff these into an object and have a single array;
     * improves clarity but adds overhead)
     */
    private final File[] mFiles;         // source file Files, for rsrc URLs
    private final ZipFile[] mZips;       // source zip files, with resources
    private final DexFile[] mDexs;       // opened, prepped DEX files

    /**应用程序本地库路径和系统的本地库路径
     * Native library path.
     */
    private final String[] mLibPaths;

    /**jar或者apk里面包含了一些Dex文件，Dex文件里面包含了类
     * Creates a {@code DexClassLoader} that finds interpreted and native
     * code.  Interpreted classes are found in a set of DEX files contained
     * in Jar or APK files.
     *
     * The path lists are separated using the character specified by
     * the "path.separator" system property, which defaults to ":".
     *
     * @param dexPath jar或者apk文件的路径，这些jar和apk包含类和资源
     *  the list of jar/apk files containing classes and resources
     * @param dexOutputDir
     *  directory where optimized DEX files should be written
     * @param libPath 本地库的路径
     *  the list of directories containing native libraries; may be null
     * @param parent 父加载器
     *  the parent class loader
     */
    public DexClassLoader(String dexPath, String dexOutputDir, String libPath,
        ClassLoader parent) {

        super(parent);

        if (dexPath == null || dexOutputDir == null)
            throw new NullPointerException();

        mRawDexPath = dexPath;
        mDexOutputPath = dexOutputDir;
        mRawLibPath = libPath;

        String[] dexPathList = mRawDexPath.split(":");
        int length = dexPathList.length;

        //System.out.println("DexClassLoader: " + dexPathList);
        mFiles = new File[length];
        mZips = new ZipFile[length];
        mDexs = new DexFile[length];

        /* open all Zip and DEX files up front */
        for (int i = 0; i < length; i++) {
            //System.out.println("My path is: " + dexPathList[i]);
            File pathFile = new File(dexPathList[i]);
            mFiles[i] = pathFile;

            if (pathFile.isFile()) {
                try {
                    mZips[i] = new ZipFile(pathFile);
                } catch (IOException ioex) {
                    // expecting IOException and ZipException
                    System.out.println("Failed opening '" + pathFile
                        + "': " + ioex);
                    //ioex.printStackTrace();
                }
								//dex文件是不包含资源的，
                /* we need both DEX and Zip, because dex has no resources */
                try {
                    String outputName =
                        generateOutputName(dexPathList[i], mDexOutputPath);
                    mDexs[i] = DexFile.loadDex(dexPathList[i], outputName, 0);
                } catch (IOException ioex) {
                    // might be a resource-only zip
                    System.out.println("Failed loadDex '" + pathFile
                        + "': " + ioex);
                }
            } else {
                if (VERBOSE_DEBUG)
                    System.out.println("Not found: " + pathFile.getPath());
            }
        }

        /*
         * Prep for native library loading.
         */
        String pathList = System.getProperty("java.library.path", ".");
        String pathSep = System.getProperty("path.separator", ":");
        String fileSep = System.getProperty("file.separator", "/");

        if (mRawLibPath != null) {
            if (pathList.length() > 0) {
                pathList += pathSep + mRawLibPath;
            }
            else {
                pathList = mRawLibPath;
            }
        }

        mLibPaths = pathList.split(pathSep);
        length = mLibPaths.length;

        // Add a '/' to the end so we don't have to do the property lookup
        // and concatenation later.
        for (int i = 0; i < length; i++) {
            if (!mLibPaths[i].endsWith(fileSep))
                mLibPaths[i] += fileSep;
            if (VERBOSE_DEBUG)
                System.out.println("Native lib path " +i+ ":  " + mLibPaths[i]);
        }
    }

    /**构造文件名，文件名以.dex结尾
     * Convert a source path name and an output directory to an output
     * file name.
     */
    private static String generateOutputName(String sourcePathName,
        String outputDir) {

        StringBuilder newStr = new StringBuilder(80);

        /* start with the output directory */
        newStr.append(outputDir);
        if (!outputDir.endsWith("/"))
            newStr.append("/");

        /* get the filename component of the path */
        String sourceFileName;
        int lastSlash = sourcePathName.lastIndexOf("/");
        if (lastSlash < 0)
            sourceFileName = sourcePathName;
        else
            sourceFileName = sourcePathName.substring(lastSlash+1);

        /*以.dex替换其他的文件名，不能使用.odex，
         * Replace ".jar", ".zip", whatever with ".dex".  We don't want to
         * use ".odex", because the build system uses that for files that
         * are paired with resource-only jar files.  If the VM can assume
         * that there's no classes.dex in the matching jar, it doesn't need
         * to open the jar to check for updated dependencies, providing a
         * slight performance boost at startup.  The use of ".dex" here
         * matches the use on files in /data/dalvik-cache.
         */
        int lastDot = sourceFileName.lastIndexOf(".");
        if (lastDot < 0)
            newStr.append(sourceFileName);
        else
            newStr.append(sourceFileName, 0, lastDot);
        newStr.append(".dex");

        if (VERBOSE_DEBUG)
            System.out.println("Output file will be " + newStr.toString());
        return newStr.toString();
    }

    /**
     * Finds a class. This method is called by {@code loadClass()} after the
     * parent ClassLoader has failed to find a loaded class of the same name.
     *
     * @param name
     *            The name of the class to search for, in a human-readable form
     *            like "java.lang.String" or "java.net.URLClassLoader$3$1".
     * @return the {@link Class} object representing the class
     * @throws ClassNotFoundException
     *             if the class cannot be found
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (VERBOSE_DEBUG)
            System.out.println("DexClassLoader " + this
                + ": findClass '" + name + "'");

        int length = mFiles.length;

        for (int i = 0; i < length; i++) {
            if (VERBOSE_DEBUG)
                System.out.println("  Now searching: " + mFiles[i].getPath());

            if (mDexs[i] != null) {
                String slashName = name.replace('.', '/');
                Class clazz = mDexs[i].loadClass(slashName, this);
                if (clazz != null) {
                    if (VERBOSE_DEBUG)
                        System.out.println("    found");
                    return clazz;
                }
            }
        }

        throw new ClassNotFoundException(name + " in loader " + this);
    }

    /**
     * Finds a resource. This method is called by {@code getResource()} after
     * the parent ClassLoader has failed to find a loaded resource of the same
     * name.
     *
     * @param name
     *            The name of the resource to find
     * @return the location of the resource as a URL, or {@code null} if the
     *         resource is not found.
     */
    @Override
    protected URL findResource(String name) {
        int length = mFiles.length;

        for (int i = 0; i < length; i++) {
            File pathFile = mFiles[i];
            ZipFile zip = mZips[i];

            if (zip.getEntry(name) != null) {
                if (VERBOSE_DEBUG)
                    System.out.println("  found " + name + " in " + pathFile);
                try {
                    // File.toURL() is compliant with RFC 1738 in always
                    // creating absolute path names. If we construct the
                    // URL by concatenating strings, we might end up with
                    // illegal URLs for relative names.
                    return new URL("jar:" + pathFile.toURL() + "!/" + name);
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (VERBOSE_DEBUG)
            System.out.println("  resource " + name + " not found");

        return null;
    }

    /**父加载器没找到的时候才会调用DexClassLoader去寻找，
     * Finds a native library. This method is called after the parent
     * ClassLoader has failed to find a native library of the same name.
     *
     * @param libname
     *            The name of the library to find
     * @return the complete path of the library, or {@code null} if the library
     *         is not found.
     */
    @Override
    protected String findLibrary(String libname) {
    	//库名转换成文件名
        String fileName = System.mapLibraryName(libname);
        for (int i = 0; i < mLibPaths.length; i++) {
            String pathName = mLibPaths[i] + fileName;
            File test = new File(pathName);

            if (test.exists()) {
                if (VERBOSE_DEBUG)
                    System.out.println("  found " + libname);
                return pathName;
            }
        }

        if (VERBOSE_DEBUG)
            System.out.println("  library " + libname + " not found");
        return null;
    }

    /**
     * Returns package information for the given package. Unfortunately, the
     * DexClassLoader doesn't really have this information, and as a non-secure
     * ClassLoader, it isn't even required to, according to the spec. Yet, we
     * want to provide it, in order to make all those hopeful callers of
     * <code>myClass.getPackage().getName()</code> happy. Thus we construct a
     * Package object the first time it is being requested and fill most of the
     * fields with dummy values. The Package object is then put into the
     * ClassLoader's Package cache, so we see the same one next time. We don't
     * create Package objects for null arguments or for the default package.
     * <p>
     * There a limited chance that we end up with multiple Package objects
     * representing the same package: It can happen when when a package is
     * scattered across different JAR files being loaded by different
     * ClassLoaders. Rather unlikely, and given that this whole thing is more or
     * less a workaround, probably not worth the effort.
     *
     * @param name
     *            the name of the class
     * @return the package information for the class, or {@code null} if there
     *         is not package information available for it
     */
    @Override
    protected Package getPackage(String name) {
        if (name != null && !name.isEmpty()) {
            synchronized(this) {
                Package pack = super.getPackage(name);

                if (pack == null) {
                    pack = definePackage(name, "Unknown", "0.0", "Unknown",
                            "Unknown", "0.0", "Unknown", null);
                }

                return pack;
            }
        }

        return null;
    }
}
