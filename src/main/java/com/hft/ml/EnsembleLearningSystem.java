package com.hft.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ensemble Learning System for HFT Trading
 * 
 * Implements state-of-the-art ensemble methods used by top HFT firms:
 * - Two Sigma: Model combination for improved accuracy
 * - Citadel Securities: Weighted ensemble for risk management
 * - Jump Trading: Dynamic model selection for speed
 * - Renaissance Technologies: Bayesian model averaging
 * 
 * Based on 2024-2025 global HFT best practices for ensemble learning
 */
public class EnsembleLearningSystem implements Serializable {
    
    private static final Logger logger = LoggerFactory.getLogger(EnsembleLearningSystem.class);
    
    // Ensemble configuration
    private final EnsembleType ensembleType;
    private final List<ModelWrapper> models;
    private final double[] modelWeights;
    private final Map<String, Double> modelPerformance;
    
    // Dynamic selection parameters
    private final double performanceDecayFactor;
    private final double minWeight;
    private final double maxWeight;
    
    // Performance tracking
    private final AtomicLong totalPredictions;
    private final AtomicLong successfulPredictions;
    private final Map<String, ModelPerformanceStats> modelStats;
    
    // Ensemble types
    public enum EnsembleType {
        WEIGHTED_AVERAGE,      // Static weighted averaging
        DYNAMIC_SELECTION,     // Dynamic model selection based on recent performance
        BAYESIAN_AVERAGING,    // Bayesian model averaging
        STACKED_GENERALIZATION, // Stacked generalization
        ADAPTIVE_WEIGHTING     // Adaptive weighting based on market conditions
    }
    
    /**
     * Model wrapper for ensemble
     */
    private static class ModelWrapper implements Serializable {
        final String modelId;
        final Object model;
        final ModelType modelType;
        double weight;
        double recentPerformance;
        long predictionCount;
        
        ModelWrapper(String modelId, Object model, ModelType modelType) {
            this.modelId = modelId;
            this.model = model;
            this.modelType = modelType;
            this.weight = 1.0;
            this.recentPerformance = 0.0;
            this.predictionCount = 0;
        }
        
        double predict(double[] features) {
            try {
                switch (modelType) {
                    case TRANSFORMER:
                        TransformerPricePredictor.PredictionResult transformerResult = 
                            ((TransformerPricePredictor) model).predict(features);
                        return transformerResult.price;
                    case TCN:
                        TemporalConvolutionalNetwork.TCNPrediction tcnResult = 
                            ((TemporalConvolutionalNetwork) model).predict(features);
                        return tcnResult.prediction;
                    case LSTM:
                        double[] lstmResult = ((LSTMPricePredictor) model).predict(features);
                        return lstmResult[0]; // predictedPrice
                    case GNN:
                        // GNN requires different input format, use default for now
                        logger.warn("GNN prediction not compatible with double[] features, using fallback");
                        return 0.0;
                    default:
                        return 0.0;
                }
            } catch (Exception e) {
                logger.error("Prediction failed for model type: {}", modelType, e);
                return 0.0;
            }
        }
        
        double predictWithConfidence(double[] features) {
            try {
                switch (modelType) {
                    case TRANSFORMER:
                        TransformerPricePredictor.PredictionResult pred = 
                            ((TransformerPricePredictor) model).predict(features);
                        return pred.price * pred.confidence;
                    case TCN:
                        TemporalConvolutionalNetwork.TCNPrediction pred = 
                            ((TemporalConvolutionalNetwork) model).predict(features);
                        return pred.prediction * pred.confidence;
                    case LSTM:
                        double[] lstmResult = ((LSTMPricePredictor) model).predict(features);
                        return lstmResult[0] * lstmResult[1]; // prediction * confidence
                    case GNN:
                        // GNN requires different input format, use fallback
                        logger.warn("GNN prediction not compatible with double[] features, using fallback");
                        return 0.0;
                    default:
                        return predict(features);
                }
            } catch (Exception e) {
                logger.error("Prediction failed for model type: {}", modelType, e);
                return 0.0;
            }
        }
    }
    
    /**
     * Model types
     */
    public enum ModelType {
        TRANSFORMER, TCN, LSTM, GNN, LINEAR, RANDOM_FOREST
    }
    
    /**
     * Model performance statistics
     */
    private static class ModelPerformanceStats implements Serializable {
        double accuracy;
        double avgLatency;
        double confidence;
        long predictionCount;
        double errorRate;
        
        ModelPerformanceStats() {
            this.accuracy = 0.0;
            this.avgLatency = 0.0;
            this.confidence = 0.0;
            this.predictionCount = 0;
            this.errorRate = 0.0;
        }
        
        void update(double accuracy, double latency, double confidence, boolean correct) {
            this.predictionCount++;
            
            // Exponential moving average
            double alpha = 0.1;
            this.accuracy = alpha * accuracy + (1 - alpha) * this.accuracy;
            this.avgLatency = alpha * latency + (1 - alpha) * this.avgLatency;
            this.confidence = alpha * confidence + (1 - alpha) * this.confidence;
            
            if (correct) {
                this.errorRate = alpha * 0.0 + (1 - alpha) * this.errorRate;
            } else {
                this.errorRate = alpha * 1.0 + (1 - alpha) * this.errorRate;
            }
        }
    }
    
    public EnsembleLearningSystem(EnsembleType ensembleType) {
        this.ensembleType = ensembleType;
        this.models = new ArrayList<>();
        this.modelWeights = new double[0];
        this.modelPerformance = new HashMap<>();
        this.modelStats = new HashMap<>();
        
        this.performanceDecayFactor = 0.95;
        this.minWeight = 0.05;
        this.maxWeight = 0.5;
        
        this.totalPredictions = new AtomicLong(0);
        this.successfulPredictions = new AtomicLong(0);
        
        logger.info("Ensemble Learning System initialized with type: {}", ensembleType);
    }
    
    /**
     * Add model to ensemble
     */
    public void addModel(String modelId, Object model, ModelType modelType) {
        if (!isModelValid(model, modelType)) {
            throw new IllegalArgumentException("Invalid model type for " + modelType);
        }
        
        ModelWrapper wrapper = new ModelWrapper(modelId, model, modelType);
        models.add(wrapper);
        
        // Update weights
        updateModelWeights();
        
        // Initialize performance tracking
        modelStats.put(modelId, new ModelPerformanceStats());
        modelPerformance.put(modelId, 0.0);
        
        logger.info("Added model {} of type {} to ensemble", modelId, modelType);
    }
    
    /**
     * Check if model is valid for ensemble
     */
    private boolean isModelValid(Object model, ModelType modelType) {
        switch (modelType) {
            case TRANSFORMER:
                return model instanceof TransformerPricePredictor && ((TransformerPricePredictor) model).isTrained();
            case TCN:
                return model instanceof TemporalConvolutionalNetwork && ((TemporalConvolutionalNetwork) model).isTrained();
            case LSTM:
                return model instanceof LSTMPricePredictor && ((LSTMPricePredictor) model).isTrained();
            case GNN:
                return model instanceof GraphNeuralNetwork && ((GraphNeuralNetwork) model).isTrained();
            default:
                return false;
        }
    }
    
    /**
     * Update model weights based on ensemble type
     */
    private void updateModelWeights() {
        int numModels = models.size();
        modelWeights = new double[numModels];
        
        switch (ensembleType) {
            case WEIGHTED_AVERAGE:
                // Equal weights initially
                for (int i = 0; i < numModels; i++) {
                    modelWeights[i] = 1.0 / numModels;
                }
                break;
                
            case DYNAMIC_SELECTION:
                // Based on recent performance
                double totalPerformance = 0;
                for (ModelWrapper model : models) {
                    totalPerformance += Math.max(0.1, model.recentPerformance);
                }
                for (int i = 0; i < numModels; i++) {
                    modelWeights[i] = Math.max(minWeight, 
                        Math.min(maxWeight, models.get(i).recentPerformance / totalPerformance));
                }
                // Normalize
                double sum = Arrays.stream(modelWeights).sum();
                for (int i = 0; i < numModels; i++) {
                    modelWeights[i] /= sum;
                }
                break;
                
            case BAYESIAN_AVERAGING:
                // Based on model confidence and accuracy
                for (int i = 0; i < numModels; i++) {
                    ModelPerformanceStats stats = modelStats.get(models.get(i).modelId);
                    modelWeights[i] = stats.confidence * (1 - stats.errorRate);
                }
                // Normalize
                double bayesSum = Arrays.stream(modelWeights).sum();
                for (int i = 0; i < numModels; i++) {
                    modelWeights[i] /= bayesSum;
                }
                break;
                
            case STACKED_GENERALIZATION:
                // Meta-learner weights (simplified)
                for (int i = 0; i < numModels; i++) {
                    modelWeights[i] = 1.0 / numModels;
                }
                break;
                
            case ADAPTIVE_WEIGHTING:
                // Adaptive based on market conditions (simplified)
                for (int i = 0; i < numModels; i++) {
                    modelWeights[i] = 1.0 / numModels;
                }
                break;
        }
    }
    
    /**
     * Make ensemble prediction
     */
    public EnsemblePrediction predict(double[] features) {
        long startTime = System.nanoTime();
        
        double prediction = 0;
        double confidence = 0;
        Map<String, Double> individualPredictions = new HashMap<>();
        
        switch (ensembleType) {
            case WEIGHTED_AVERAGE:
                for (int i = 0; i < models.size(); i++) {
                    ModelWrapper model = models.get(i);
                    double modelPred = model.predict(features);
                    prediction += modelPred * modelWeights[i];
                    individualPredictions.put(model.modelId, modelPred);
                }
                confidence = calculateEnsembleConfidence();
                break;
                
            case DYNAMIC_SELECTION:
                // Select best performing model
                ModelWrapper bestModel = models.stream()
                    .max(Comparator.comparing(m -> m.recententPerformance))
                    .orElse(models.get(0));
                prediction = bestModel.predict(features);
                confidence = bestModel.recententPerformance;
                individualPredictions.put(bestModel.modelId, prediction);
                break;
                
            case BAYESIAN_AVERAGING:
                double weightedSum = 0;
                double totalWeight = 0;
                for (int i = 0; i < models.size(); i++) {
                    ModelWrapper model = models.get(i);
                    ModelPerformanceStats stats = modelStats.get(model.modelId);
                    double modelPred = model.predictWithConfidence(features);
                    double weight = stats.confidence * (1 - stats.errorRate);
                    weightedSum += modelPred * weight;
                    totalWeight += weight;
                    individualPredictions.put(model.modelId, model.predict(features));
                }
                prediction = weightedSum / totalWeight;
                confidence = calculateEnsembleConfidence();
                break;
                
            case STACKED_GENERALIZATION:
                // Simplified stacked generalization
                for (int i = 0; i < models.size(); i++) {
                    ModelWrapper model = models.get(i);
                    double modelPred = model.predict(features);
                    prediction += modelPred * modelWeights[i];
                    individualPredictions.put(model.modelId, modelPred);
                }
                confidence = calculateEnsembleConfidence();
                break;
                
            case ADAPTIVE_WEIGHTING:
                // Adaptive weighting based on input features
                for (int i = 0; i < models.size(); i++) {
                    ModelWrapper model = models.get(i);
                    double adaptiveWeight = calculateAdaptiveWeight(model, features);
                    double modelPred = model.predict(features);
                    prediction += modelPred * adaptiveWeight;
                    individualPredictions.put(model.modelId, modelPred);
                }
                confidence = calculateEnsembleConfidence();
                break;
        }
        
        // Update performance tracking
        long endTime = System.nanoTime();
        totalPredictions.incrementAndGet();
        
        return new EnsemblePrediction(prediction, confidence, individualPredictions, 
                                  endTime - startTime);
    }
    
    /**
     * Calculate ensemble confidence
     */
    private double calculateEnsembleConfidence() {
        double avgConfidence = 0;
        int count = 0;
        
        for (ModelWrapper model : models) {
            ModelPerformanceStats stats = modelStats.get(model.modelId);
            avgConfidence += stats.confidence;
            count++;
        }
        
        return count > 0 ? avgConfidence / count : 0.5;
    }
    
    /**
     * Calculate adaptive weight based on input features
     */
    private double calculateAdaptiveWeight(ModelWrapper model, double[] features) {
        // Simplified adaptive weighting based on feature variance
        double featureVariance = calculateVariance(features);
        double modelComplexity = getModelComplexity(model.modelType);
        
        // Higher weight for simpler models in high variance conditions
        double adaptiveFactor = 1.0 / (1.0 + featureVariance * modelComplexity);
        
        return modelWeights[models.indexOf(model)] * adaptiveFactor;
    }
    
    /**
     * Calculate feature variance
     */
    private double calculateVariance(double[] features) {
        double mean = 0;
        for (double feature : features) {
            mean += feature;
        }
        mean /= features.length;
        
        double variance = 0;
        for (double feature : features) {
            variance += Math.pow(feature - mean, 2);
        }
        
        return variance / features.length;
    }
    
    /**
     * Get model complexity
     */
    private double getModelComplexity(ModelType modelType) {
        switch (modelType) {
            case TRANSFORMER: return 4.0;
            case GNN: return 3.5;
            case TCN: return 3.0;
            case LSTM: return 2.5;
            default: return 1.0;
        }
    }
    
    /**
     * Update model performance
     */
    public void updateModelPerformance(String modelId, double targetValue, double actualValue, 
                                        double latency, double confidence) {
        ModelWrapper model = models.stream()
            .filter(m -> m.modelId.equals(modelId))
            .findFirst()
            .orElse(null);
        
        if (model != null) {
            boolean correct = Math.abs(targetValue - actualValue) < 0.01; // 1% tolerance
            double accuracy = correct ? 1.0 : 0.0;
            
            // Update model performance
            model.recentPerformance = performanceDecayFactor * model.recentPerformance + 
                                      (1 - performanceDecayFactor) * accuracy;
            model.predictionCount++;
            
            // Update detailed stats
            ModelPerformanceStats stats = modelStats.get(modelId);
            stats.update(accuracy, latency, confidence, correct);
            
            // Update ensemble weights if dynamic
            if (ensembleType == EnsembleType.DYNAMIC_SELECTION) {
                updateModelWeights();
            }
            
            // Update global performance
            if (correct) {
                successfulPredictions.incrementAndGet();
            }
        }
    }
    
    /**
     * Get ensemble statistics
     */
    public EnsembleStats getEnsembleStats() {
        long total = totalPredictions.get();
        long successful = successfulPredictions.get();
        double accuracy = total > 0 ? (double) successful / total : 0.0;
        
        return new EnsembleStats(
            models.size(),
            ensembleType,
            accuracy,
            modelWeights.clone(),
            modelStats,
            total
        );
    }
    
    /**
     * Get best performing model
     */
    public String getBestModel() {
        return models.stream()
            .max(Comparator.comparing(m -> m.recententPerformance))
            .map(m -> m.modelId)
            .orElse(null);
    }
    
    /**
     * Get model performance ranking
     */
    public List<String> getModelRanking() {
        return models.stream()
            .sorted((a, b) -> Double.compare(b.recentPerformance, a.recententPerformance))
            .map(m -> m.modelId)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Ensemble prediction result
     */
    public static class EnsemblePrediction implements Serializable {
        public final double prediction;
        public final double confidence;
        public final Map<String, Double> individualPredictions;
        public final long inferenceTime;
        
        EnsemblePrediction(double prediction, double confidence, 
                           Map<String, Double> individualPredictions, long inferenceTime) {
            this.prediction = prediction;
            this.confidence = confidence;
            this.individualPredictions = individualPredictions;
            this.inferenceTime = inferenceTime;
        }
        
        @Override
        public String toString() {
            return String.format("Prediction: %.6f, Confidence: %.2f%%, Models: %d", 
                               prediction, confidence * 100, individualPredictions.size());
        }
    }
    
    /**
     * Ensemble statistics
     */
    public static class EnsembleStats implements Serializable {
        public final int numModels;
        public final EnsembleType ensembleType;
        public final double accuracy;
        public final double[] modelWeights;
        public final Map<String, ModelPerformanceStats> modelStats;
        public final long totalPredictions;
        
        EnsembleStats(int numModels, EnsembleType ensembleType, double accuracy,
                       double[] modelWeights, Map<String, ModelPerformanceStats> modelStats,
                       long totalPredictions) {
            this.numModels = numModels;
            this.ensembleType = ensembleType;
            this.accuracy = accuracy;
            this.modelWeights = modelWeights;
            this.modelStats = modelStats;
            this.totalPredictions = totalPredictions;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Ensemble: %d models, Type: %s, Accuracy: %.2f%%, Predictions: %d",
                numModels, ensembleType, accuracy * 100, totalPredictions
            );
        }
    }
}
