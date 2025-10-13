# MSRawJava
A cross-platform Java/C#/Rust package for efficiently reading Thermo .raw and timsTOF .d files.

**Building**
<br>You will need Rustc and Cargo to build the Rust components.
<br>You will need DotNet and Protobuf to build the C# components.
<br>You will need Maven to build the Java components. 

Maven should run the build scripts as part of any compile phase. You need to run scripts/build-all-net.sh before scripts/build-all-rust.sh because the net.sh script cleans the target space. Sometimes Maven gets confused with the pom.xml, so if you get errors, the recommended build order is:
<br>> scripts/build-all-net.sh
<br>> scripts/build-all-rust.sh
<br>> mvn clean package