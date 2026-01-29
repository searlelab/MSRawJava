#!/bin/sh

program="msrawjava";
baseversion=0;

branch=$(git symbolic-ref --short HEAD);

let "year=`date +%Y`-2020";
let "day=`date +%j`";
current=${baseversion}.${year}.${day};
let "next=${year}+1";
next=${baseversion}.${next}.0;

echo "Updating ${program} $branch from [${current}] to [${next}]";
echo `find core/src/main -name '*.java' -exec wc {} \; | awk '{sum=sum+$1} END {print sum}'` "total lines of Java Core production code"
echo `find core/src/test -name '*.java' -exec wc {} \; | awk '{sum=sum+$1} END {print sum}'` "total lines of Java Core test code"
echo `find gui/src/main -name '*.java' -exec wc {} \; | awk '{sum=sum+$1} END {print sum}'` "total lines of Java GUI production code"
echo `find gui/src/test -name '*.java' -exec wc {} \; | awk '{sum=sum+$1} END {print sum}'` "total lines of Java GUI test code"
echo `find rust-jni/src -name '*.rs' -exec wc {} \; | awk '{sum=sum+$1} END {print sum}'` "total lines of Rust code"
echo `find thermo-raw-server/Program.cs -name '*.cs' -exec wc {} \; | awk '{sum=sum+$1} END {print sum}'` "total lines of C# code"

TAG=v${current}

echo $TAG;
exit; #FIXME

mvn versions:set -DnewVersion="${current}"

git commit -am "Update to version for release ${TAG}."
git tag "${TAG}" # optional
mvn clean package;
mvn versions:set -DnewVersion="${next}-SNAPSHOT"
git commit -am "Update to next SNAPSHOT version."

echo "Finished updating from [${current}] to [${next}]";
echo "Remember to run \`git push origin --tags\` or \`git push origin ${TAG}\`!"
