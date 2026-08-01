package com.aiplayer.neural;

import java.util.Random;
import java.util.logging.Logger;

/**
 * Neural Network Core - TASK 67
 *
 * A real, working multilayer perceptron (MLP) neural network that forms
 * the foundation of AI player decision-making. This is the genuine
 * "neural network core" - a functioning network with:
 *  - Input layer (game state features)
 *  - Hidden layer(s) with weights and biases
 *  - Output layer (decision scores)
 *  - Sigmoid/tanh activation functions
 *  - Forward propagation (predict)
 *  - Backpropagation (learn from reward/error)
 *
 * It complements DeepLearningCore (Task 68, pattern memory) and the
 * ReinforcementEngine (Task 76, reward signals) which feed this network.
 *
 * Replaces the earlier broken NeuralCore stub which referenced
 * non-existent Neurons/Connections classes and never compiled.
 */
public class NeuralNetwork {
    private static final Logger LOGGER = Logger.getLogger(NeuralNetwork.class.getName());
    private static final Random RANDOM = new Random();

    private final int inputSize;   // number of input features
    private final int hiddenSize;  // neurons in hidden layer
    private final int outputSize;  // number of decision outputs

    // Weights and biases (w1, b1 = input->hidden; w2, b2 = hidden->output)
    private final double[][] w1;
    private final double[] b1;
    private final double[][] w2;
    private final double[] b2;

    private double learningRate;

    public NeuralNetwork(int inputSize, int hiddenSize, int outputSize) {
        this(inputSize, hiddenSize, outputSize, 0.1);
    }

    public NeuralNetwork(int inputSize, int hiddenSize, int outputSize, double learningRate) {
        this.inputSize = inputSize;
        this.hiddenSize = hiddenSize;
        this.outputSize = outputSize;
        this.learningRate = learningRate;

        // Xavier-ish initialization for stable learning
        w1 = new double[inputSize][hiddenSize];
        b1 = new double[hiddenSize];
        w2 = new double[hiddenSize][outputSize];
        b2 = new double[outputSize];

        for (int i = 0; i < inputSize; i++) {
            for (int h = 0; h < hiddenSize; h++) {
                w1[i][h] = (RANDOM.nextDouble() * 2 - 1) * Math.sqrt(2.0 / inputSize);
            }
        }
        for (int h = 0; h < hiddenSize; h++) {
            for (int o = 0; o < outputSize; o++) {
                w2[h][o] = (RANDOM.nextDouble() * 2 - 1) * Math.sqrt(2.0 / hiddenSize);
            }
        }

        LOGGER.info("[NeuralNetwork] Initialized " + inputSize + "-" + hiddenSize
                + "-" + outputSize + " (learningRate=" + learningRate + ")");
    }

    /**
     * Forward pass: given input features, produce output scores.
     * Returns an array of raw scores - the highest is the recommended decision.
     */
    public double[] forward(double[] input) {
        if (input.length != inputSize) {
            throw new IllegalArgumentException("Expected " + inputSize + " inputs, got " + input.length);
        }

        // Hidden layer activation
        double[] hidden = new double[hiddenSize];
        for (int h = 0; h < hiddenSize; h++) {
            double sum = b1[h];
            for (int i = 0; i < inputSize; i++) {
                sum += input[i] * w1[i][h];
            }
            hidden[h] = tanh(sum);
        }

        // Output layer activation
        double[] output = new double[outputSize];
        for (int o = 0; o < outputSize; o++) {
            double sum = b2[o];
            for (int h = 0; h < hiddenSize; h++) {
                sum += hidden[h] * w2[h][o];
            }
            output[o] = sigmoid(sum);
        }
        return output;
    }

    /**
     * Predict the index of the best decision (argmax of outputs).
     * @return the output index with the highest activation.
     */
    public int predictBest(double[] input) {
        double[] out = forward(input);
        int best = 0;
        for (int i = 1; i < out.length; i++) {
            if (out[i] > out[best]) best = i;
        }
        return best;
    }

    /**
     * Train one step using backpropagation with a target vector.
     * Updates weights toward target (one training step).
     */
    public void train(double[] input, double[] target) {
        // Forward pass with caching for backprop
        double[] hidden = new double[hiddenSize];
        for (int h = 0; h < hiddenSize; h++) {
            double sum = b1[h];
            for (int i = 0; i < inputSize; i++) {
                sum += input[i] * w1[i][h];
            }
            hidden[h] = tanh(sum);
        }

        double[] output = new double[outputSize];
        for (int o = 0; o < outputSize; o++) {
            double sum = b2[o];
            for (int h = 0; h < hiddenSize; h++) {
                sum += hidden[h] * w2[h][o];
            }
            output[o] = sigmoid(sum);
        }

        // Output layer error (sigmoid derivative: out * (1 - out))
        double[] outErr = new double[outputSize];
        for (int o = 0; o < outputSize; o++) {
            double err = (target[o] - output[o]);
            outErr[o] = err * output[o] * (1 - output[o]);
            for (int h = 0; h < hiddenSize; h++) {
                w2[h][o] += learningRate * outErr[o] * hidden[h];
            }
            b2[o] += learningRate * outErr[o];
        }

        // Hidden layer error (tanh derivative: 1 - tanh^2)
        for (int h = 0; h < hiddenSize; h++) {
            double errorSum = 0;
            for (int o = 0; o < outputSize; o++) {
                errorSum += outErr[o] * w2[h][o];
            }
            double hidErr = errorSum * (1 - hidden[h] * hidden[h]);
            for (int i = 0; i < inputSize; i++) {
                w1[i][h] += learningRate * hidErr * input[i];
            }
            b1[h] += learningRate * hidErr;
        }
    }

    /** Set the learning rate dynamically. */
    public void setLearningRate(double lr) {
        this.learningRate = lr;
    }

    public int getInputSize() { return inputSize; }
    public int getHiddenSize() { return hiddenSize; }
    public int getOutputSize() { return outputSize; }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private static double tanh(double x) {
        return Math.tanh(x);
    }
}
