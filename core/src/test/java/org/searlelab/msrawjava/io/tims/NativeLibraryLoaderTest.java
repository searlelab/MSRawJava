package org.searlelab.msrawjava.io.tims;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NativeLibraryLoaderTest {

	private String originalOsName;
	private String originalOsArch;

	@BeforeEach
	void setUp() {
		originalOsName=System.getProperty("os.name");
		originalOsArch=System.getProperty("os.arch");
	}

	@AfterEach
	void tearDown() {
		System.setProperty("os.name", originalOsName);
		System.setProperty("os.arch", originalOsArch);
	}

	@Test
	void privateConstructorIsPresentForUtilityClass() throws Exception {
		Constructor<NativeLibraryLoader> ctor=NativeLibraryLoader.class.getDeclaredConstructor();
		ctor.setAccessible(true);
		ctor.newInstance();
	}

	@Test
	void loadRejectsUnsupportedOperatingSystems() {
		System.setProperty("os.name", "Solaris");
		System.setProperty("os.arch", "sparc");

		assertThrows(UnsatisfiedLinkError.class, NativeLibraryLoader::load);
	}
}
