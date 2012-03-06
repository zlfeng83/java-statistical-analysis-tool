
package jsat.distributions.multivariate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import jsat.classifiers.DataPoint;
import jsat.distributions.empirical.KernelDensityEstimator;
import jsat.distributions.empirical.kernelfunc.EpanechnikovKF;
import jsat.distributions.empirical.kernelfunc.KernelFunction;
import jsat.exceptions.UntrainedModelException;
import jsat.linear.Vec;
import jsat.linear.VecPaired;
import jsat.linear.distancemetrics.DistanceMetric;
import jsat.linear.distancemetrics.EuclideanDistance;
import jsat.linear.distancemetrics.TrainableDistanceMetric;
import jsat.linear.vectorcollection.VPTree.VPTreeFactory;
import jsat.linear.vectorcollection.VectorCollection;
import jsat.linear.vectorcollection.VectorCollectionFactory;
import jsat.linear.vectorcollection.VectorCollectionUtils;
import jsat.math.OnLineStatistics;

/**
 * MetricKDE is a generalization of the {@link KernelDensityEstimator} to the multivariate case. 
 * A {@link KernelFunction} is used to weight the contribution of each data point, and a 
 * {@link DistanceMetric } is used to effectively alter the shape of the kernel. The MetricKDE uses 
 * one bandwidth parameter, which can be estimated using a nearest neighbor approach, or tuned by hand. 
 * The bandwidth of the MetricKDE can not be estimated en the same way as the univariate case. 
 * 
 * @author Edward Raff
 */
public class MetricKDE extends MultivariateKDE
{
    private KernelFunction kf;
    private double bandwidth;
    private DistanceMetric distanceMetric;
    private VectorCollectionFactory<VecPaired<Integer, Vec>> vcf;
    private VectorCollection<VecPaired<Integer, Vec>> vecCollection;
    private int defaultK;
    private double defaultStndDev;
    
    /**
     * When estimating the bandwidth, the distances of the k'th nearest 
     * neighbors are used to perform the estimate. The default value of
     * this k is {@value #DEFAULT_K}
     */
    public static final int DEFAULT_K = 3;
    
    /**
     * When estimating the bandwidth, the distances of the k'th nearest
     * neighbors are used to perform the estimate. The default number of
     * standard deviations from the mean to add to the bandwidth estimate
     * is {@value #DEFAULT_STND_DEV}
     */
    public static final double DEFAULT_STND_DEV = 2.0;

    /**
     * Creates a new KDE object that still needs a data set to model the distribution of
     */
    public MetricKDE()    
    {
        this(new EpanechnikovKF(), new EuclideanDistance(), new VPTreeFactory<VecPaired<Integer, Vec>>());
    }

    /**
     * Creates a new KDE object that still needs a data set to model the distribution of
     * 
     * @param distanceMetric the distance metric to use
     */
    public MetricKDE(DistanceMetric distanceMetric)    
    {
        this(new EpanechnikovKF(), distanceMetric, new VPTreeFactory<VecPaired<Integer, Vec>>());
    }
    
    /**
     * Creates a new KDE object that still needs a data set to model the distribution of
     * @param distanceMetric the distance metric to use
     * @param vcf a factory to generate vector collection from
     */
    public MetricKDE(DistanceMetric distanceMetric, VectorCollectionFactory<VecPaired<Integer, Vec>> vcf)    
    {
        this(new EpanechnikovKF(), distanceMetric, vcf);
    }
    
    /**
     * Creates a new KDE object that still needs a data set to model the distribution of
     * @param kf the kernel function to use
     * @param distanceMetric the distance metric to use
     * @param vcf a factory to generate vector collection from
     */
    public MetricKDE(KernelFunction kf, DistanceMetric distanceMetric, VectorCollectionFactory<VecPaired<Integer, Vec>> vcf)
    {
        this(kf, distanceMetric, vcf, DEFAULT_K, DEFAULT_STND_DEV);
    }
    
    /**
     * Creates a new KDE object that still needs a data set to model the distribution of
     * @param kf the kernel function to use
     * @param distanceMetric the distance metric to use
     * @param vcf a factory to generate vector collection from
     * @param defaultK the default neighbor to use when estimating the bandwidth
     * @param defaultStndDev the default multiple of standard deviations to add when estimating the bandwidth
     */
    public MetricKDE(KernelFunction kf, DistanceMetric distanceMetric, VectorCollectionFactory<VecPaired<Integer, Vec>> vcf, int defaultK, double defaultStndDev)
    {
        this.kf = kf;
        this.distanceMetric = distanceMetric;
        this.vcf = vcf;
        setDefaultK(defaultK);
        setDefaultStndDev(defaultStndDev);
    }

    /**
     * Sets the bandwidth used to estimate the density of the underlying distribution. Too small a bandwidth 
     * results in high variance, while too large causes high bias. 
     * 
     * @param bandwidth the bandwidth to use for estimation
     * @throws ArithmeticException if the bandwidth given is not a positive number 
     */
    public void setBandwith(double bandwidth)
    {
        if(bandwidth <= 0 || Double.isNaN(bandwidth) || Double.isInfinite(bandwidth))
            throw new ArithmeticException("Invalid bandwith given, bandwith must be a positive number, not " + bandwidth);
        this.bandwidth = bandwidth;
    }

    /**
     * Returns the current bandwidth used
     * @return the current bandwidth
     */
    public double getBandwith()
    {
        return bandwidth;
    }

    /**
     * When estimating the bandwidth, the mean of the k'th nearest neighbors to each data point
     * is used. This value controls the default value of k used when it is not specified. 
     * 
     * @param defaultK 
     */
    public void setDefaultK(int defaultK)
    {
        if(defaultK <= 0)
            throw new ArithmeticException("At least one neighbor must be taken into acount, " + defaultK + " is invalid");
        this.defaultK = defaultK;
    }

    /**
     * Returns the default value of the k'th nearest neighbor to use when not specified. 
     * @return the default neighbor used to estimate the bandwidth when not specified 
     */
    public int getDefaultK()
    {
        return defaultK;
    }

    /**
     * When estimating the bandwidth, the mean of the neighbor distances is used, and a multiple of 
     * the standard deviations is added. This controls the multiplier value used when the bandwidth is not specified. 
     * Zero and negative multipliers are allowed, but a negative multiplier may result in the fitting failing. 
     * 
     * @param defaultStndDev the multiple of the standard deviation to add the to bandwidth estimate
     */
    public void setDefaultStndDev(double defaultStndDev)
    {
        if(Double.isInfinite(defaultStndDev) || Double.isNaN(defaultStndDev))
            throw new ArithmeticException("The number of standard deviations to remove must be number, not " + defaultStndDev);
        this.defaultStndDev = defaultStndDev;
    }

    /**
     * Returns the multiple of the standard deviations that is added to the bandwidth estimate 
     * @return the multiple of the standard deviations that is added to the bandwidth estimate 
     */
    public double getDefaultStndDev()
    {
        return defaultStndDev;
    }
    
    @Override
    public MetricKDE clone()
    {
        MetricKDE clone = new MetricKDE(kf, distanceMetric.clone(), vcf.clone());
        clone.bandwidth = this.bandwidth;
        if(this.vecCollection != null)
            clone.vecCollection = this.vecCollection.clone();
        return clone;
    }

    @Override
    public List<VecPaired<Double, VecPaired<Integer, Vec>>> getNearby(Vec x)
    {
        if(vecCollection == null)
            throw new UntrainedModelException("Model has not yet been created");
        List<VecPaired<Double, VecPaired<Integer, Vec>>> nearBy = vecCollection.search(x, bandwidth*kf.cutOff());
        //Normalize from their distances to their weights bye kernel function
        for(VecPaired<Double, VecPaired<Integer, Vec>> result : nearBy)
            result.setPair(kf.k(result.getPair()/bandwidth));
        return nearBy;
    }
        
    public double pdf(Vec x)
    {
        List<VecPaired<Double, VecPaired<Integer, Vec>>> nearBy = getNearby(x);
        
        if(nearBy.isEmpty())
            return 0;
        
        double PDF = 0;
        for(VecPaired<Double, VecPaired<Integer, Vec>> result : nearBy)
            PDF+= result.getPair();
        
        return PDF / (vecCollection.size() * Math.pow(bandwidth, nearBy.get(0).length()));
    }

    /**
     * Sets the KDE to model the density of the given data set with the specified bandwidth
     * @param dataSet the data set to model the density of
     * @param bandwith the bandwidth 
     * @return <tt>true</tt> if the model was fit, <tt>false</tt> if it could not be fit. 
     */
    public <V extends Vec> boolean setUsingData(List<V> dataSet, double bandwith)
    {
        return setUsingData(dataSet, bandwith, null);
    }
    
    /**
     * Sets the KDE to model the density of the given data set with the specified bandwidth
     * @param dataSet the data set to model the density of
     * @param bandwith the bandwidth 
     * @param threadpool the source of threads for parallel construction
     * @return <tt>true</tt> if the model was fit, <tt>false</tt> if it could not be fit. 
     */
    public <V extends Vec> boolean setUsingData(List<V> dataSet, double bandwith, ExecutorService threadpool)
    {
        setBandwith(bandwith);
        List<VecPaired<Integer, Vec>> indexVectorPair = new ArrayList<VecPaired<Integer, Vec>>(dataSet.size());
        for(int i = 0; i < dataSet.size(); i++)
            indexVectorPair.add(new VecPaired<Integer, Vec>(dataSet.get(i), i));
        
        TrainableDistanceMetric.trainIfNeeded(distanceMetric, dataSet, threadpool);
        
        if(threadpool == null)
            vecCollection = vcf.getVectorCollection(indexVectorPair, distanceMetric);
        else
            vecCollection = vcf.getVectorCollection(indexVectorPair, distanceMetric, threadpool);
        
        return true;
    }
    
    /**
     * Sets the KDE to model the density of the given data set by estimating the bandwidth by using
     * the <tt>k</tt> nearest neighbors of each data point. 
     * @param dataSet the data set to model the density of
     * @param k the number of neighbors to use to estimate the bandwidth
     * @return <tt>true</tt> if the model was fit, <tt>false</tt> if it could not be fit. 
     */
    public <V extends Vec> boolean setUsingData(List<V> dataSet, int k)
    {
        return setUsingData(dataSet, k, defaultStndDev);
    }
    
    /**
     *  Sets the KDE to model the density of the given data set by estimating the bandwidth by using
     * the <tt>k</tt> nearest neighbors of each data point. 
     * @param dataSet the data set to model the density of
     * @param k the number of neighbors to use to estimate the bandwidth
     * @param threadpool the source of threads for computation
     * @return <tt>true</tt> if the model was fit, <tt>false</tt> if it could not be fit. 
     */
    public <V extends Vec> boolean setUsingData(List<V> dataSet, int k, ExecutorService threadpool)
    {
        return setUsingData(dataSet, k, defaultK, threadpool);
    }
    
    /**
     * Sets the KDE to model the density of the given data set by estimating the bandwidth 
     * by using the <tt>k</tt> nearest neighbors of each data data point. <br>
     * The bandwidth estimate is calculate as the mean of the distances of the k'th nearest
     * neighbor plus <tt>stndDevs</tt> standard deviations added to the mean. 
     * 
     * @param dataSet the data set to model the density of
     * @param k the number of neighbors to use to estimate the bandwidth
     * @param stndDevs the multiple of the standard deviation to add to the mean of the distances
     * @return <tt>true</tt> if the model was fit, <tt>false</tt> if it could not be fit. 
     */
    public <V extends Vec> boolean setUsingData(List<V> dataSet, int k, double stndDevs)
    {
        return setUsingData(dataSet, k, stndDevs, null);
    }
    
    /**
     * Sets the KDE to model the density of the given data set by estimating the bandwidth 
     * by using the <tt>k</tt> nearest neighbors of each data data point. <br>
     * The bandwidth estimate is calculate as the mean of the distances of the k'th nearest
     * neighbor plus <tt>stndDevs</tt> standard deviations added to the mean. 
     * 
     * @param dataSet the data set to model the density of
     * @param k the number of neighbors to use to estimate the bandwidth
     * @param stndDevs the multiple of the standard deviation to add to the mean of the distances
     * @param threadpool the source of threads to use for computation
     * @return <tt>true</tt> if the model was fit, <tt>false</tt> if it could not be fit. 
     */
    public <V extends Vec> boolean setUsingData(List<V> dataSet, int k, double stndDevs, ExecutorService threadpool)
    {
        List<VecPaired<Integer, Vec>> indexVectorPair = new ArrayList<VecPaired<Integer, Vec>>(dataSet.size());
        for(int i = 0; i < dataSet.size(); i++)
            indexVectorPair.add(new VecPaired<Integer, Vec>(dataSet.get(i), i));
        TrainableDistanceMetric.trainIfNeeded(distanceMetric, dataSet, threadpool);
        vecCollection = vcf.getVectorCollection(indexVectorPair, distanceMetric);
        
        //Take the average of the k'th neighbor distance to use as the bandwith
        OnLineStatistics stats;
        if(threadpool == null)//k+1 b/c the first nearest neighbor will be itself
            stats = VectorCollectionUtils.getKthNeighborStats(vecCollection, dataSet, k + 1);
        else
            try
            {
                stats = VectorCollectionUtils.getKthNeighborStats(vecCollection, dataSet, k + 1, threadpool);
            }
            catch (InterruptedException ex)
            {
                Logger.getLogger(MetricKDE.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
            catch (ExecutionException ex)
            {
                Logger.getLogger(MetricKDE.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }

        setBandwith(stats.getMean() + stats.getStandardDeviation() * stndDevs);

        return true;
    }
    
    public <V extends Vec> boolean setUsingData(List<V> dataSet)
    {
        return setUsingData(dataSet, defaultK);
    }

    @Override
    public <V extends Vec> boolean setUsingData(List<V> dataSet, ExecutorService threadpool)
    {
        return setUsingData(dataSet, defaultK, threadpool);
    }
    
    public boolean setUsingDataList(List<DataPoint> dataPoints)
    {
        List<Vec> dataSet = new ArrayList<Vec>(dataPoints.size());
        for(DataPoint dp : dataPoints)
            dataSet.add(dp.getNumericalValues());
        return setUsingData(dataSet);
    }

    @Override
    public boolean setUsingDataList(List<DataPoint> dataPoints, ExecutorService threadpool)
    {
        List<Vec> dataSet = new ArrayList<Vec>(dataPoints.size());
        for(DataPoint dp : dataPoints)
            dataSet.add(dp.getNumericalValues());
        return setUsingData(dataSet, threadpool);
    }

    /**
     * Sampling not yet supported 
     * @param count
     * @param rand
     * @return will not return
     * @throws UnsupportedOperationException not yet implemented
     */
    public List<Vec> sample(int count, Random rand)
    {
        //TODO implement sampling
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
