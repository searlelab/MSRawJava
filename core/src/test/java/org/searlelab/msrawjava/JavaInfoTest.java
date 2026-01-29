package org.searlelab.msrawjava;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class JavaInfoTest {

	@Test
	void printJavaInfo() {
		String javaHome=System.getProperty("java.home");
		String javaVersion=System.getProperty("java.version");
		String javaVendor=System.getProperty("java.vendor");
		String classPath=System.getProperty("java.class.path");
		String modulePath=System.getProperty("jdk.module.path");
		String upgradeModulePath=System.getProperty("jdk.module.upgrade.path");

		System.out.println("JAVA_HOME (runtime): "+javaHome);
		System.out.println("JAVA_VERSION: "+javaVersion);
		System.out.println("JAVA_VENDOR: "+javaVendor);
		System.out.println("JAVA_CLASS_PATH: "+classPath);
		System.out.println("JDK_MODULE_PATH: "+modulePath);
		System.out.println("JDK_MODULE_UPGRADE_PATH: "+upgradeModulePath);
		try {
			Module attachModule=com.sun.tools.attach.VirtualMachine.class.getModule();
			System.out.println("JDK_ATTACH_MODULE: "+attachModule.getName());
			System.out.println("JDK_ATTACH_LOCATION: "+attachModule.getDescriptor());
		} catch (Throwable t) {
			System.out.println("JDK_ATTACH_MODULE: <unavailable> ("+t.getClass().getSimpleName()+": "+t.getMessage()+")");
		}

		assertNotNull(javaHome);
		assertNotNull(javaVersion);
	}
}
