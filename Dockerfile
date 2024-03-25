# Use an official OpenJDK runtime as a parent image
FROM openjdk:11-jre-slim

# Set the working directory to /app
WORKDIR /app

# Copy the WAR file from the local filesystem to the container at /app
COPY C:/Users/brian/Downloads/spring/spring-translator/target/lugha-translator.war /app/lugha-translator.war

# Make port 8080 available to the world outside this container
EXPOSE 8080

# Run the application using java -jar command with the WAR file
CMD ["java", "-jar", "lugha-translator.war"]
