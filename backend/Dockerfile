# Immagine del backend per Render (profilo prod). Due fasi: la prima compila il jar, la seconda lo esegue con il solo
# JRE, così l'immagine finale non contiene sorgenti, Maven né JDK.

# --- 1. build ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# prima solo il wrapper e il pom: le dipendenze si scaricano in uno strato che Docker riusa finché il pom non cambia
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

COPY src src
# i test non girano qui: si eseguono in locale prima di ogni commit e il deploy parte solo da main dopo la merge
# (la build gratuita di Render ha poca memoria e i test allungherebbero di minuti ogni deploy)
RUN ./mvnw -q -DskipTests package

# --- 2. esecuzione ---
FROM eclipse-temurin:25-jre
WORKDIR /app

# utente senza privilegi: l'app non gira come root
RUN useradd --system --no-create-home quackfit
USER quackfit

COPY --from=build /app/target/*.jar app.jar

# Render gratuito ha 512 MB: heap al 60% della memoria del contenitore (circa 300 MB), il resto per metaspace, thread e
# buffer; SerialGC è il garbage collector più leggero per un solo processore. Si può cambiare da Render impostando
# JAVA_TOOL_OPTIONS, senza ricostruire l'immagine.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC"
ENV SPRING_PROFILES_ACTIVE=prod

# la porta la sceglie Render (variabile PORT, letta da application-prod.properties); 8080 è quella di default
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
