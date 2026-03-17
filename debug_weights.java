public class debug_weights {
    public static void main(String[] args) {
        int INPUT_SIZE = 50;
        int HIDDEN_SIZE = 64;
        int OUTPUT_SIZE = 2;
        int NUM_LAYERS = 2;
        
        int inputWeights = INPUT_SIZE * HIDDEN_SIZE;
        int lstmWeightsPerLayer = 4 * HIDDEN_SIZE * (INPUT_SIZE + HIDDEN_SIZE);
        int lstmWeightsTotal = lstmWeightsPerLayer * NUM_LAYERS;
        int outputWeights = HIDDEN_SIZE * OUTPUT_SIZE;
        
        int totalWeights = inputWeights + lstmWeightsTotal + outputWeights;
        int totalBiases = (HIDDEN_SIZE * 4 * NUM_LAYERS) + OUTPUT_SIZE;
        
        System.out.println("INPUT_SIZE: " + INPUT_SIZE);
        System.out.println("HIDDEN_SIZE: " + HIDDEN_SIZE);
        System.out.println("OUTPUT_SIZE: " + OUTPUT_SIZE);
        System.out.println("NUM_LAYERS: " + NUM_LAYERS);
        System.out.println("inputWeights: " + inputWeights);
        System.out.println("lstmWeightsPerLayer: " + lstmWeightsPerLayer);
        System.out.println("lstmWeightsTotal: " + lstmWeightsTotal);
        System.out.println("outputWeights: " + outputWeights);
        System.out.println("totalWeights: " + totalWeights);
        System.out.println("totalBiases: " + totalBiases);
        
        // Test indexing
        int layerOffset = 1; // Second layer
        int baseLayerOffset = inputWeights + (layerOffset * lstmWeightsPerLayer);
        System.out.println("baseLayerOffset: " + baseLayerOffset);
        
        int i = 0;
        int index = baseLayerOffset + 7 * HIDDEN_SIZE + i;
        System.out.println("Testing index: " + index);
        System.out.println("Index < totalWeights: " + (index < totalWeights));
    }
}
