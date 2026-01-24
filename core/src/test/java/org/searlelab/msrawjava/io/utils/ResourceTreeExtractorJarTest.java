package org.searlelab.msrawjava.io.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceTreeExtractorJarTest {

	@TempDir
	Path tmp;

	@Test
	void extractsJarResourcesAndSetsExecutableFlag() throws Exception {
		Path classesDir=tmp.resolve("classes");
		Path jarPath=tmp.resolve("test-resources.jar");
		Files.createDirectories(classesDir);

		Path srcDir=tmp.resolve("src");
		Path pkgDir=srcDir.resolve("org/searlelab/msrawjava/io/utils/testjar");
		Files.createDirectories(pkgDir);
		Path srcFile=pkgDir.resolve("Anchor.java");
		Files.writeString(srcFile, "package org.searlelab.msrawjava.io.utils.testjar; public class Anchor {}", StandardCharsets.UTF_8);

		JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "JavaCompiler must be available to build the test jar");
		int compileResult=compiler.run(null, null, null, "-d", classesDir.toString(), srcFile.toString());
		assertEquals(0, compileResult);

		Path classFile=classesDir.resolve("org/searlelab/msrawjava/io/utils/testjar/Anchor.class");
		assertTrue(Files.exists(classFile));

		try (JarOutputStream jar=new JarOutputStream(Files.newOutputStream(jarPath))) {
			jar.putNextEntry(new JarEntry("org/searlelab/msrawjava/io/utils/testjar/Anchor.class"));
			jar.write(Files.readAllBytes(classFile));
			jar.closeEntry();

			addJarDir(jar, "payload/");
			addJarDir(jar, "payload/nested/");
			addJarFile(jar, "payload/one.txt", "one");
			addJarFile(jar, "payload/nested/two.txt", "two");
			addJarFile(jar, "payload/MSRaw.Thermo.Server", "bin");
		}

		try (URLClassLoader loader=new URLClassLoader(new URL[] {jarPath.toUri().toURL()}, getClass().getClassLoader())) {
			Class<?> anchor=Class.forName("org.searlelab.msrawjava.io.utils.testjar.Anchor", true, loader);

			Path dest=tmp.resolve("out");
			ResourceTreeExtractor.extractDirectory(anchor, "payload", dest);

			Path one=dest.resolve("one.txt");
			Path two=dest.resolve("nested/two.txt");
			Path server=dest.resolve("MSRaw.Thermo.Server");
			assertTrue(Files.exists(one));
			assertTrue(Files.exists(two));
			assertTrue(Files.exists(server));
			assertEquals("one", Files.readString(one, StandardCharsets.UTF_8));
			assertEquals("two", Files.readString(two, StandardCharsets.UTF_8));

			if (Files.getFileStore(server).supportsFileAttributeView("posix")) {
				Set<PosixFilePermission> perms=Files.getPosixFilePermissions(server);
				assertTrue(perms.contains(PosixFilePermission.OWNER_EXECUTE));
			}
		}
	}

	private static void addJarDir(JarOutputStream jar, String name) throws IOException {
		JarEntry entry=new JarEntry(name);
		jar.putNextEntry(entry);
		jar.closeEntry();
	}

	private static void addJarFile(JarOutputStream jar, String name, String content) throws IOException {
		JarEntry entry=new JarEntry(name);
		jar.putNextEntry(entry);
		try (ByteArrayInputStream in=new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
			in.transferTo(jar);
		}
		jar.closeEntry();
	}
}
