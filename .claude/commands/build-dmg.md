Build the macOS DMG installer for the Church Admin application.

Run the following two commands in sequence:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn clean package -Pmac -DskipTests && JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home mvn jpackage:jpackage -Pmac
```

The DMG will be written to `target/dist/Heilige Barbara Parochie-1.0.dmg`.