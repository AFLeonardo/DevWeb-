# Salidas de las versiones

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.0)

2026-09-25T15:27:03.583-06:00  INFO 6260 --- [backend-academico] [           main] m.e.b.BackendAcademicoApplication        : Starting BackendAcademicoApplication using Java 25.0.4.1 with PID 6260 (C:\Users\Leonardo\Documents\FCFM\Back-END\U01\inicio\target\classes started by Leonardo in C:\Users\Leonardo\Documents\FCFM\Back-END\U01\inicio)
2026-09-25T15:27:03.586-06:00  INFO 6260 --- [backend-academico] [           main] m.e.b.BackendAcademicoApplication        : No active profile set, falling back to 1 default profile: "default"
2026-09-25T15:27:04.426-06:00  INFO 6260 --- [backend-academico] [           main] o.s.boot.tomcat.TomcatWebServer          : Tomcat initialized with port 8081 (http)
2026-09-25T15:27:04.447-06:00  INFO 6260 --- [backend-academico] [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2026-09-25T15:27:04.448-06:00  INFO 6260 --- [backend-academico] [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/11.0.22]
```

# Pruebas unitarias

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v4.1.0)

2026-09-25T15:29:34.613-06:00  INFO 13908 --- [backend-academico] [           main] m.e.b.BackendAcademicoApplicationTests   : Starting BackendAcademicoApplicationTests using Java 25.0.4.1 with PID 13908 (started by Leonardo in C:\Users\Leonardo\Documents\FCFM\Back-END\U01\inicio)
2026-09-25T15:29:34.615-06:00  INFO 13908 --- [backend-academico] [           main] m.e.b.BackendAcademicoApplicationTests   : No active profile set, falling back to 1 default profile: "default"
2026-09-25T15:29:35.803-06:00  INFO 13908 --- [backend-academico] [           main] m.e.b.BackendAcademicoApplicationTests   : Started BackendAcademicoApplicationTests in 1.48 seconds (process running for 2.838)
Mockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
WARNING: A Java agent has been loaded dynamically (C:\Users\Leonardo\.m2\repository\net\bytebuddy\byte-buddy-agent\1.18.10\byte-buddy-agent-1.18.10.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.834 s -- in mx.edu.backendacademico.BackendAcademicoApplicationTests
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  5.682 s
[INFO] Finished at: 2026-09-25T15:29:36-06:00
[INFO] ------------------------------------------------------------------------
```

## Por que no exige recompilar?
Porque Spring Boot utiliza el patron de configuracion externalizada. El puerto del server web no
esta hardcodeado en el codigo fuente de Java ni en los bytecode compiladores.

Durante la fase de inicio. Spring instancia el Environment leyendo fuentes de configuracion segun la jerarquia
predefinida.

```
argumentos CLI > propiedades del sistema Java > variables de entorno > application.properties
```

