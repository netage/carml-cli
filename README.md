# carml-cli
Interface for CARML library. At this moment works only with xml files.

#How to build
Build with maven
```
mvn clean package
```

#How to use
- Possible to convert a single file:
```
java -jar cli-0.0.1-SNAPSHOT-jar-with-dependencies.jar -i inputfile.xml -m rml.mapping.ttl -o output
```

- Possible to convert a folder:
```
java -jar cli-0.0.1-SNAPSHOT-jar-with-dependencies.jar -f /folder -m /rml.mapping.ttl -o /output.ttl
```

