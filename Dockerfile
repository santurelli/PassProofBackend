FROM eclipse-temurin:17-jre-alpine
WORKDIR /deployments

# Optimize Docker layer caching by copying the fast-jar folder structure
# We copy with user 185 (non-root) permissions
COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 target/quarkus-app/*.jar /deployments/
COPY --chown=185 target/quarkus-app/app/ /deployments/app/
COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
CMD ["java", "-jar", "/deployments/quarkus-run.jar"]
