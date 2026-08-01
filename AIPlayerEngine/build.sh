#!/bin/bash
echo "Building AI Player Engine..."

# Check if Maven is available
if command -v mvn &> /dev/null; then
    echo "Using Maven to build..."
    mvn clean compile
else
    echo "Compiling manually with javac..."
    mkdir -p build/classes
    
    # Compile all Java files
    find src/main/java -name "*.java" -exec javac -d build/classes {} \;
    
    # Create JAR
    mkdir -p build/jar
    jar cvf build/jar/ai-player-engine.jar -C build/classes .
fi

echo "Build complete!"
ls -la build/jar/ai-player-engine.jar 2>/dev/null || echo "Check build/classes for compiled files"
