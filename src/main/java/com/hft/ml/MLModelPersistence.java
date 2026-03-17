package com.hft.ml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ML Model Persistence Manager
 * 
 * Implements industry best practices for model persistence:
 * - Model versioning and rollback
 * - Hot-swappable models without trading interruption
 * - Model metadata tracking (accuracy, training date, performance)
 * - Atomic model updates to prevent corruption
 * 
 * Used by top HFT firms for zero-downtime model deployment
 */
public class MLModelPersistence {
    
    private static final String MODEL_BASE_DIR = "models";
    private static final String MODEL_BACKUP_DIR = "models/backup";
    private static final String METADATA_FILE = "model_metadata.json";
    
    // Active models in memory
    private final Map<String, TrainedModel> activeModels;
    private final Map<String, ModelMetadata> modelMetadata;
    
    // Model directories
    private final Path baseDir;
    private final Path backupDir;
    
    public MLModelPersistence() {
        this.activeModels = new ConcurrentHashMap<>();
        this.modelMetadata = new ConcurrentHashMap<>();
        this.baseDir = Paths.get(MODEL_BASE_DIR);
        this.backupDir = Paths.get(MODEL_BACKUP_DIR);
        
        // Create directories
        createDirectories();
        
        // Load existing models
        loadExistingModels();
    }
    
    /**
     * Save trained model with metadata
     */
    public boolean saveModel(String modelName, TrainedModel model, ModelMetadata metadata) {
        try {
            // Create backup of current model if exists
            if (activeModels.containsKey(modelName)) {
                backupModel(modelName);
            }
            
            // Save model to file
            Path modelPath = baseDir.resolve(modelName + ".model");
            saveModelToFile(model, modelPath);
            
            // Save metadata
            metadata.lastUpdated = LocalDateTime.now();
            metadata.modelPath = modelPath.toString();
            saveMetadata(modelName, metadata);
            
            // Update active model
            activeModels.put(modelName, model);
            modelMetadata.put(modelName, metadata);
            
            System.out.printf("✅ Model '%s' saved successfully%n", modelName);
            return true;
            
        } catch (Exception e) {
            System.err.printf("❌ Failed to save model '%s': %s%n", modelName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Load model from disk
     */
    public TrainedModel loadModel(String modelName) {
        try {
            // Check if model is already loaded
            if (activeModels.containsKey(modelName)) {
                return activeModels.get(modelName);
            }
            
            // Load metadata
            ModelMetadata metadata = loadMetadata(modelName);
            if (metadata == null) {
                System.err.printf("❌ No metadata found for model '%s'%n", modelName);
                return null;
            }
            
            // Load model from file
            Path modelPath = Paths.get(metadata.modelPath);
            TrainedModel model = loadModelFromFile(modelPath);
            
            if (model != null) {
                activeModels.put(modelName, model);
                modelMetadata.put(modelName, metadata);
                System.out.printf("✅ Model '%s' loaded successfully%n", modelName);
            }
            
            return model;
            
        } catch (Exception e) {
            System.err.printf("❌ Failed to load model '%s': %s%n", modelName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Hot-swap model without trading interruption
     */
    public boolean hotSwapModel(String modelName, TrainedModel newModel, ModelMetadata newMetadata) {
        try {
            // Create backup of current model
            if (activeModels.containsKey(modelName)) {
                backupModel(modelName);
            }
            
            // Save new model to temporary file
            Path tempModelPath = baseDir.resolve(modelName + ".tmp.model");
            saveModelToFile(newModel, tempModelPath);
            
            // Atomic swap: rename temporary to actual model file
            Path finalModelPath = baseDir.resolve(modelName + ".model");
            Files.move(tempModelPath, finalModelPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            
            // Update metadata
            newMetadata.lastUpdated = LocalDateTime.now();
            newMetadata.modelPath = finalModelPath.toString();
            saveMetadata(modelName, newMetadata);
            
            // Update active model (atomic operation)
            activeModels.put(modelName, newModel);
            modelMetadata.put(modelName, newMetadata);
            
            System.out.printf("🔄 Model '%s' hot-swapped successfully%n", modelName);
            return true;
            
        } catch (Exception e) {
            System.err.printf("❌ Failed to hot-swap model '%s': %s%n", modelName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Rollback to previous model version
     */
    public boolean rollbackModel(String modelName) {
        try {
            // Find latest backup
            Path backupPath = findLatestBackup(modelName);
            if (backupPath == null) {
                System.err.printf("❌ No backup found for model '%s'%n", modelName);
                return false;
            }
            
            // Load backup model
            TrainedModel backupModel = loadModelFromFile(backupPath);
            if (backupModel == null) {
                System.err.printf("❌ Failed to load backup model '%s'%n", modelName);
                return false;
            }
            
            // Get backup metadata
            ModelMetadata backupMetadata = loadMetadata(modelName + "_backup");
            if (backupMetadata == null) {
                backupMetadata = modelMetadata.get(modelName); // Fallback to current metadata
            }
            
            // Perform hot-swap with backup
            return hotSwapModel(modelName, backupModel, backupMetadata);
            
        } catch (Exception e) {
            System.err.printf("❌ Failed to rollback model '%s': %s%n", modelName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get active model
     */
    public TrainedModel getModel(String modelName) {
        return activeModels.get(modelName);
    }
    
    /**
     * Get model metadata
     */
    public ModelMetadata getMetadata(String modelName) {
        return modelMetadata.get(modelName);
    }
    
    /**
     * List all available models
     */
    public Map<String, ModelMetadata> listModels() {
        return new HashMap<>(modelMetadata);
    }
    
    /**
     * Check if model is loaded
     */
    public boolean isModelLoaded(String modelName) {
        return activeModels.containsKey(modelName);
    }
    
    /**
     * Create necessary directories
     */
    private void createDirectories() {
        try {
            if (!Files.exists(baseDir)) {
                Files.createDirectories(baseDir);
            }
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
            }
        } catch (IOException e) {
            System.err.printf("❌ Failed to create directories: %s%n", e.getMessage());
        }
    }
    
    /**
     * Load existing models from disk
     */
    private void loadExistingModels() {
        try {
            if (Files.exists(baseDir)) {
                Files.list(baseDir)
                    .filter(path -> path.toString().endsWith(".model"))
                    .forEach(path -> {
                        String modelName = path.getFileName().toString().replace(".model", "");
                        loadModel(modelName);
                    });
            }
        } catch (IOException e) {
            System.err.printf("❌ Failed to load existing models: %s%n", e.getMessage());
        }
    }
    
    /**
     * Save model to file
     */
    private void saveModelToFile(TrainedModel model, Path path) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            oos.writeObject(model);
            oos.flush();
        }
    }
    
    /**
     * Load model from file
     */
    private TrainedModel loadModelFromFile(Path path) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            return (TrainedModel) ois.readObject();
        }
    }
    
    /**
     * Save metadata
     */
    private void saveMetadata(String modelName, ModelMetadata metadata) {
        try {
            Path metadataPath = baseDir.resolve(modelName + "_metadata.json");
            String json = metadata.toJson();
            Files.write(metadataPath, json.getBytes());
        } catch (IOException e) {
            System.err.printf("❌ Failed to save metadata for '%s': %s%n", modelName, e.getMessage());
        }
    }
    
    /**
     * Load metadata
     */
    private ModelMetadata loadMetadata(String modelName) {
        try {
            Path metadataPath = baseDir.resolve(modelName + "_metadata.json");
            if (!Files.exists(metadataPath)) {
                return null;
            }
            
            String json = new String(Files.readAllBytes(metadataPath));
            return ModelMetadata.fromJson(json);
        } catch (IOException e) {
            System.err.printf("❌ Failed to load metadata for '%s': %s%n", modelName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Backup current model
     */
    private void backupModel(String modelName) {
        try {
            Path currentModelPath = baseDir.resolve(modelName + ".model");
            if (Files.exists(currentModelPath)) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                Path backupPath = backupDir.resolve(modelName + "_" + timestamp + ".model");
                Files.copy(currentModelPath, backupPath);
                
                // Also backup metadata
                Path currentMetadataPath = baseDir.resolve(modelName + "_metadata.json");
                if (Files.exists(currentMetadataPath)) {
                    Path backupMetadataPath = backupDir.resolve(modelName + "_" + timestamp + "_metadata.json");
                    Files.copy(currentMetadataPath, backupMetadataPath);
                }
                
                System.out.printf("📦 Model '%s' backed up to '%s'%n", modelName, backupPath.getFileName());
            }
        } catch (IOException e) {
            System.err.printf("❌ Failed to backup model '%s': %s%n", modelName, e.getMessage());
        }
    }
    
    /**
     * Find latest backup
     */
    private Path findLatestBackup(String modelName) {
        try {
            return Files.list(backupDir)
                .filter(path -> path.getFileName().toString().startsWith(modelName + "_") &&
                              path.getFileName().toString().endsWith(".model"))
                .max((p1, p2) -> {
                    String name1 = p1.getFileName().toString();
                    String name2 = p2.getFileName().toString();
                    return name1.compareTo(name2); // Lexicographic comparison works for timestamp format
                })
                .orElse(null);
        } catch (IOException e) {
            System.err.printf("❌ Failed to find backup for model '%s': %s%n", modelName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Trained Model Interface
     */
    public interface TrainedModel extends Serializable {
        /**
         * Get model accuracy
         */
        double getAccuracy();
        
        /**
         * Get model version
         */
        String getVersion();
        
        /**
         * Check if model is ready for inference
         */
        boolean isReady();
    }
    
    /**
     * Model Metadata
     */
    public static class ModelMetadata implements Serializable {
        public String modelName;
        public String version;
        public double accuracy;
        public String trainingDate;
        public LocalDateTime lastUpdated;
        public String modelPath;
        public Map<String, Object> hyperparameters;
        public Map<String, Double> performanceMetrics;
        public String description;
        
        public ModelMetadata(String modelName, String version) {
            this.modelName = modelName;
            this.version = version;
            this.hyperparameters = new HashMap<>();
            this.performanceMetrics = new HashMap<>();
            this.trainingDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        
        public String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"modelName\":\"").append(modelName).append("\",");
            json.append("\"version\":\"").append(version).append("\",");
            json.append("\"accuracy\":").append(accuracy).append(",");
            json.append("\"trainingDate\":\"").append(trainingDate).append("\",");
            json.append("\"lastUpdated\":\"").append(lastUpdated.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",");
            json.append("\"modelPath\":\"").append(modelPath).append("\",");
            json.append("\"description\":\"").append(description != null ? description : "").append("\",");
            json.append("\"hyperparameters\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : hyperparameters.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                first = false;
            }
            json.append("},");
            json.append("\"performanceMetrics\":{");
            first = true;
            for (Map.Entry<String, Double> entry : performanceMetrics.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                first = false;
            }
            json.append("}");
            json.append("}");
            return json.toString();
        }
        
        public static ModelMetadata fromJson(String json) {
            // Simple JSON parsing - in production would use proper JSON library
            ModelMetadata metadata = new ModelMetadata("", "");
            
            // Parse basic fields (simplified)
            if (json.contains("\"modelName\"")) {
                int start = json.indexOf("\"modelName\":\"") + 13;
                int end = json.indexOf("\"", start);
                metadata.modelName = json.substring(start, end);
            }
            
            if (json.contains("\"version\"")) {
                int start = json.indexOf("\"version\":\"") + 11;
                int end = json.indexOf("\"", start);
                metadata.version = json.substring(start, end);
            }
            
            if (json.contains("\"accuracy\"")) {
                int start = json.indexOf("\"accuracy\":") + 11;
                int end = json.indexOf(",", start);
                if (end == -1) end = json.indexOf("}", start);
                metadata.accuracy = Double.parseDouble(json.substring(start, end));
            }
            
            return metadata;
        }
        
        @Override
        public String toString() {
            return String.format("ModelMetadata{name='%s', version='%s', accuracy=%.4f, trained=%s}",
                               modelName, version, accuracy, trainingDate);
        }
    }
}
