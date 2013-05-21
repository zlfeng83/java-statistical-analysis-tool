package jsat.classifiers.svm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import jsat.classifiers.CategoricalResults;
import jsat.classifiers.ClassificationDataSet;
import jsat.classifiers.Classifier;
import jsat.classifiers.DataPoint;
import jsat.distributions.kernels.KernelTrick;
import jsat.exceptions.FailedToFitException;
import jsat.exceptions.UntrainedModelException;
import jsat.linear.Vec;
import jsat.parameters.Parameter;
import jsat.parameters.Parameterized;
import jsat.utils.IndexTable;
import jsat.utils.random.XORWOW;

/**
 * Implementation of the Stochastic Batch Perceptron (SBP) algorithm. Despite
 * its name, it solves the kernelized SVM problem. Because it is done 
 * stochastically, it may not produce Support Vectors that the standard SVM 
 * algorithm learns. It can learn at most one SV per iteration. 
 * 
 * See:<br>
 * Cotter, A., Shalev-Shwartz, S., & Srebro, N. (2012). <i>The Kernelized 
 * Stochastic Batch Perceptron</i>. International Conference on Machine 
 * Learning. Learning. Retrieved from <a href="http://arxiv.org/abs/1204.0566">
 * here</a>
 * 
 * @author Edward Raff
 */
public class SBP extends SupportVectorLearner implements Classifier, Parameterized
{
    private double nu = 0.1;
    private int iterations;
    private double burnInFraction = 1.0/5.0;

    /**
     * Creates a new SBP SVM learner
     * @param kernel the kernel to use
     * @param cacheMode the type of kernel cache to use
     */
    public SBP(KernelTrick kernel, CacheMode cacheMode, int iterations, double v)
    {
        super(kernel, cacheMode);
        setIterations(iterations);
        setNu(v);
    }

    /**
     * Copy constructor 
     * @param other the object to copy
     */
    protected SBP(SBP other)
    {
        this(other.getKernel().clone(), other.getCacheMode(), other.iterations, other.nu);
        if(other.alphas != null)
            this.alphas = Arrays.copyOf(other.alphas, other.alphas.length);
    }
    
    @Override
    public SBP clone()
    {
        return new SBP(this);
    }

    /**
     * Sets the number of iterations to go through. At most one SV can be 
     * learned per iteration. If more iterations are done than there are SVs, it
     * is highly likely that O(n) SVs will be used, making the model very dense.
     * It may take far fewer iterations of the algorithm than there are data 
     * points to get good accuracy. 
     * @param iterations the number of iterations of the algorithm to perform
     */
    public void setIterations(int iterations)
    {
        this.iterations = iterations;
    }

    /**
     * Returns the number of iterations the algorithm will perform
     * @return the number of iterations the algorithm will perform
     */
    public int getIterations()
    {
        return iterations;
    }

    /**
     * The nu parameter of the SVM is a value in the range [0, 1]. Nu is a 
     * theoretical upper bound on the number of errors made by the SVM. In 
     * practice, is nu is set too small far more errors will occur and a bad 
     * decision boundary will be learned. A value of 0 corresponds to a 
     * perfectly linearly separable data set. 
     * 
     * @param nu 
     */
    public void setNu(double nu)
    {
        if(Double.isNaN(nu) || nu < 0 || nu > 1)
            throw new IllegalArgumentException("nu must be in the range [0, 1]");
        this.nu = nu;
    }

    /**
     * Returns the nu SVM parameter
     * @return the nu SVM parameter
     */
    public double getNu()
    {
        return nu;
    }
    

    @Override
    public CategoricalResults classify(DataPoint data)
    {
        if(vecs == null)
            throw new UntrainedModelException("Classifier has yet to be trained");
        
        CategoricalResults cr = new CategoricalResults(2);
        
        double sum = kEvalSum(data.getNumericalValues());

        //SVM only says yess / no, can not give a percentage
        if(sum < 0)
            cr.setProb(0, 1.0);
        else
            cr.setProb(1, 1.0);
        
        return cr;
    }

    @Override
    public void trainC(ClassificationDataSet dataSet, ExecutorService threadPool)
    {
        trainC(dataSet, threadPool);
    }

    @Override
    public void trainC(ClassificationDataSet dataSet)
    {
        if(dataSet.getClassSize() != 2)
            throw new FailedToFitException("SBP supports only binary classification");
        
        final int n = dataSet.getSampleSize();
        /**
         * First index where we start summing for the average
         */
        final int T_0 = 1+(int) (burnInFraction*n);
        /*
         * Respone values
         */
        double[] C = new double[n];
        double[] CSum = new double[n];
        alphas = new double[n];
        double[] alphasSum = new double[n];

        double[] y = new double[n];
        vecs = new Vec[n];
        for(int i = 0; i < n; i++)
        {
            y[i] = dataSet.getDataPointCategory(i)*2-1;
            vecs[i] = dataSet.getDataPoint(i).getNumericalValues();
        }
        
        Random rand = new XORWOW();
        double maxKii = 0;
        for(int i = 0; i < n; i++)
            maxKii = Math.max(maxKii, kEval(vecs[i], vecs[i]));//avoid starting the cache on the diagonal
        
        setCacheMode(getCacheMode());//Initiates the cahce
        
        final double eta_0 = 1/Math.sqrt(maxKii);
        
        double rSqrd = 0;
        
        for(int t = 1; t <= iterations; t++)
        {
            final double eta = eta_0/Math.sqrt(t);
            final double gamma = findGamma(C, n*nu);

            //Samply uniformly from C[i] <= gamma
            int attempts = 0;//you get 5 attempts to find one quickly
            int i;
            do
            {
                i = rand.nextInt(n);
                attempts++;
            }
            while(C[i] > gamma && attempts < 5);
            
            if(C[i] > gamma)//find one the slow way
            {
                int candidates = 0;
                for(int j = 0; j < C.length; j++)
                {
                    if(C[j] < gamma)
                        candidates++;
                }
                
                if(candidates == 0)
                    throw new FailedToFitException("BUG: please report");
                
                int randCand = rand.nextInt(candidates);
                i = 0;
                for(int j = 0; j < C.length && i < randCand; j++)
                    if(C[i] < gamma)
                        i++;
            }
            
            
            alphas[i] += eta;
            rSqrd += 2*eta*C[i]+eta*eta*kEval(i, i);
            final double y_i = y[i];
            for(int j = 0; j < n; j++)
                C[j] += eta*y_i*y[j]*kEval(i, j);
            
            if(rSqrd > 1)//1^2 = 1, so jsut use sqrd version
            {
                final double rInv = 1/Math.sqrt(rSqrd);
                
                for(int j = 0; j < n; j++)
                {
                    C[j] *= rInv;
                    alphas[j] *= rInv;
                }
                
                rSqrd = 1;
            }

            if(t >= T_0)
                for(int j = 0; j < n; j++)
                {
                    alphasSum[j] += alphas[j];
                    CSum[j] += C[j];
                }
        }
        
        //Take the averages
        for (int j = 0; j < n; j++)
        {
            alphas[j] = alphasSum[j]/(iterations-T_0);
            C[j] = CSum[j]/(iterations-T_0);
        }
        double gamma = findGamma(C, n*nu);
        for (int j = 0; j < n; j++)
            alphas[j] /= gamma;
        
        //Clean up to only the SVs
        int supportVectorCount = 0;
        for(int i = 0; i < vecs.length; i++)
            if(alphas[i] != 0)//its a support vector
            {
                vecs[supportVectorCount] = vecs[i];
                alphas[supportVectorCount++] = alphas[i]*y[i];
            }

        vecs = Arrays.copyOfRange(vecs, 0, supportVectorCount);
        alphas = Arrays.copyOfRange(alphas, 0, supportVectorCount);
        
        it = null;
        setCacheMode(null);
        setAlphas(alphas);
    }

    @Override
    public boolean supportsWeightedData()
    {
        return false;
    }
    
    private IndexTable it;

    //TODO add bias version of findGamma
    
    private double findGamma(double[] C, double d)
    {
        if(it == null )
            it = new IndexTable(C);
        else
        {
            it.reset();
            it.sort(C);
        }
        
        double sum = 0;
        double max;
        double finalScore = 0, prevScore = 0;
        
        int i;
        for(i = 0; i < it.length(); i++)
        {
            max = C[it.index(i)];
            sum += max;
            
            double score = max*i-sum;
            prevScore = finalScore;
            finalScore = (d-max*i+sum)/i+max;
            
            if(score >= d)
                break;
        }
        
        return prevScore;
    }
    
    private List<Parameter> params = Parameter.getParamsFromMethods(this);
    private Map<String, Parameter> paramMap = Parameter.toParameterMap(params);
    
    @Override
    public List<Parameter> getParameters()
    {
        List<Parameter> retParams = new ArrayList<Parameter>(params);
        retParams.addAll(getKernel().getParameters());
        return retParams;
    }

    @Override
    public Parameter getParameter(String paramName)
    {
        Parameter toRet = paramMap.get(paramName);
        if(toRet == null)
            toRet = getKernel().getParameter(paramName);
        return toRet;
    }
}
