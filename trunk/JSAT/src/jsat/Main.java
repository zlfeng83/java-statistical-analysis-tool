
package jsat;

import java.util.List;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import jsat.classifiers.ClassificationDataSet;
import jsat.classifiers.Classifier;
import jsat.classifiers.DataPoint;
import jsat.classifiers.NominalToNumeric;
import jsat.classifiers.NumericalToHistogram;
import jsat.classifiers.bayesian.NaiveBayes;
import jsat.classifiers.knn.NearestNeighbour;
import jsat.classifiers.knn.NearestNeighbourKDTree;
import jsat.classifiers.OneVSAll;
import jsat.classifiers.svm.PlatSMO;
import jsat.classifiers.trees.ID3;
import jsat.distributions.Gamma;
import jsat.distributions.Kolmogorov;
import jsat.math.rootFinding.Zeroin;
import jsat.math.rootFinding.Secant;
import jsat.distributions.Normal;
import jsat.distributions.Uniform;
import jsat.distributions.Weibull;
import jsat.distributions.kernels.LinearKernel;
import jsat.distributions.kernels.PolynomialKernel;
import jsat.distributions.kernels.RBFKernel;
import jsat.linear.DenseMatrix;
import jsat.linear.Matrix;
import jsat.linear.SparceVector;
import jsat.linear.distancemetrics.ChebyshevDistance;
import jsat.linear.distancemetrics.CosineDistance;
import jsat.linear.distancemetrics.EuclideanDistance;
import jsat.linear.distancemetrics.KernelDistance;
import jsat.linear.distancemetrics.ManhattanDistance;
import jsat.math.ContinuedFraction;
import jsat.math.Function;
import jsat.math.SpecialMath;
import jsat.math.integration.Romberg;
import jsat.math.integration.Trapezoidal;
import jsat.math.rootFinding.Bisection;
import jsat.math.rootFinding.RiddersMethod;
import jsat.utils.IndexTable;
import static java.lang.Math.*;
import static jsat.math.SpecialMath.*;

/**
 *
 * @author Edward Raff
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService threadPool = Executors.newFixedThreadPool(threads, new ThreadFactory() { 

            public Thread newThread(Runnable r)
            {
                Thread thrd = new Thread(r);
                thrd.setDaemon(true);
                return thrd;
            }
        });
        
        DenseMatrix A = new DenseMatrix(new double[][] 
        {
            {1, 5, 4, 8, 9},
            {1, 5, 7, 3, 7},
            {0, 3, 8, 5, 6},
            {3, 8, 0, 7, 0},
            {1, 9, 2, 9, 6}
        } );
        
        
        DenseMatrix C = new DenseMatrix(new double[][] 
        {
            {1, 6, 8, 3, 1, 5, 10},
            {5, 5, 3, 7, 2, 10, 0},
            {8, 0, 5, 7, 9, 1, 8},
            {9, 3, 2, 7, 2, 4, 8},
            {1, 2, 6, 5, 8, 1, 9}
        } );
        
        
//        Matrix[] lup = ((DenseMatrix) C.transpose()).lup();
//        
//        for(Matrix m : lup)
//            System.out.println(m);
//        
//        System.out.println(lup[0].multiply(lup[1]));
        
        
        DenseMatrix bigMatrix = Matrix.random(4000, 4000, new Random(3));
        
        long start = System.currentTimeMillis();
//        bigMatrix.lup(threadPool);
        bigMatrix.multiply(bigMatrix, threadPool);
        long stop = System.currentTimeMillis();
        System.out.println( (stop - start)/1000.0 + " seconds" );
        
        
        String path = "C:\\Users\\eman7613\\Desktop\\UCI\\nominal\\";
//        String sFile = path + "iris.arff";
//        String sFile = "/Users/eman7613/Desktop/datasets-UCI/UCI/vehicle.arff";
//        String sFile = path + "balance-scale.arff";
//        String sFile = path + "glass.arff";
//        String sFile = path + "waveform-5000.arff";
//        String sFile = path + "wine.arff";
        
//        String sFile = path + "sonar.arff";
//        String sFile = path + "ionosphere.arff";
//        String sFile = path + "diabetes.arff";
//        String sFile = path + "breast-w.arff";
        String sFile = path + "heart-statlog.arff";
//        String sFile = path + "optdigits.arff";
//        String sFile = path + "pendigits.arff";//Excelent example for advanced kNN. Naive: 40s, KDTree: 0.55
        
        
        //Categorical datasets with all categorical attributes
//        String sFile = path + "vote.arff";
//        String sFile = path + "nursery.arff";
//        String sFile = path + "mfeat-pixel.arff";//240 attributes! WOW

        File f = new File(sFile);
        
        List<DataPoint> dataPoints = ARFFLoader.loadArffFile(f);
        
        ClassificationDataSet cds = new ClassificationDataSet(dataPoints, dataPoints.get(0).numCategoricalValues()-1); 
        //Possible Filters
//        cds.applyTransform(new NominalToNumeric(cds.getNumNumericalVars(), cds.getCategories()));
//        cds.applyTransform(new NumericalToHistogram(cds));
        
        List<ClassificationDataSet> lcds = cds.cvSet(10);
        
        
        Classifier classifier = new NaiveBayes();
//        Classifier classifier = new NearestNeighbour(3, false);
//        Classifier classifier = new NearestNeighbourKDTree(3, false); 
//        Classifier classifier = new PlatSMO(new RBFKernel(4));
//        Classifier classifier = new OneVSAll(new PlatSMO(new RBFKernel(12.5))); 
        
//        Classifier classifier = new ID3();
        
        int wrong = 0, right = 0;
        long trainingTime = 0, classificationTime = 0;
        
        
        for(int i = 0; i < lcds.size(); i++)
        {
            ClassificationDataSet trainSet = ClassificationDataSet.comineAllBut(lcds, i);
            ClassificationDataSet testSet = lcds.get(i);
            
            

            long startTrain = System.currentTimeMillis();
//            classifier.trainC(trainSet);
            classifier.trainC(trainSet, threadPool);
            trainingTime += (System.currentTimeMillis() - startTrain);
            
            for(int j = 0; j < testSet.getPredicting().getNumOfCategories(); j++)
            {
                for (DataPoint dp : testSet.getSamples(j))
                {
                    long stratClass = System.currentTimeMillis();
                    if (classifier.classify(dp).mostLikely() == j)
                        right++;
                    else
                        wrong++;
                    classificationTime += (System.currentTimeMillis() - stratClass);
                }
            }
        }
        
        
        System.out.println("right = " + right + "\twrong = " + wrong + "\t%correct = " + ((double)right)/(wrong+right));
        System.out.println("Time spent training is: " + trainingTime/1000.0 + " seconds");
        System.out.println("Time spent classifiying is: " + classificationTime/1000.0 + " seconds");
        
//        Gamma gam = new Gamma(188.80827627270023, 0.026570162948900487);
//        
//        for(double x = 3; x < 7; x+=0.10)
//            System.out.print(x + ", ");
//        System.out.println();
//        for(double x = 3; x < 7; x+=0.10)
//            System.out.print(gam.pdf(x) + ", "); 
        
        
        
    }

}