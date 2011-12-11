
package jsat.clustering;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import jsat.DataSet;
import jsat.SimpleDataSet;
import jsat.classifiers.DataPoint;
import jsat.clustering.SeedSelectionMethods.SeedSelection;
import jsat.linear.distancemetrics.DistanceMetric;
import jsat.linear.distancemetrics.EuclideanDistance;

/**
 *
 * @author Edward Raff
 */
public class CLARA extends PAM
{
    /**
     * The number of samples to take
     */
    private int sampleSize;
    /**
     * The number of times to do sampling
     */
    private int sampleCount;
    private boolean autoSampleSize;

    public CLARA(int sampleSize, int sampleCount, DistanceMetric dm, Random rand, SeedSelection seedSelection)
    {
        super(dm, rand, seedSelection);
        this.sampleSize = sampleSize;
        this.sampleCount = sampleCount;
        this.autoSampleSize = false;
    }
    
    public CLARA(int sampleCount, DistanceMetric dm, Random rand, SeedSelection seedSelection)
    {
        super(dm, rand, seedSelection);
        this.sampleSize = -1;
        this.sampleCount = sampleCount;
        this.autoSampleSize = true;
    }

    public CLARA(DistanceMetric dm, Random rand, SeedSelection seedSelection)
    {
        this(5, dm, rand, seedSelection);
    }

    public CLARA(DistanceMetric dm, Random rand)
    {
        this(dm, rand, SeedSelection.KPP);
    }

    public CLARA(DistanceMetric dm)
    {
        this(dm, new Random());
    }

    public CLARA()
    {
        this(new EuclideanDistance());
    }
    
    /**
     * 
     * @return the number of times {@link PAM} will be applied to a sample from the data set. 
     */
    public int getSampleCount()
    {
        return sampleCount;
    }

    /**
     * Sets the number of times {@link PAM} will be applied to different samples from the data set. 
     * 
     * @param sampleCount the number of times to apply sampeling. 
     */
    public void setSampleCount(int sampleCount)
    {
        this.sampleCount = sampleCount;
    }

    /**
     * 
     * @return the number of samples that will be taken to perform {@link PAM} on. 
     */
    public int getSampleSize()
    {
        return sampleSize;
    }

    /**
     * Sets the number of samples CLARA should take from the data set to perform {@link PAM} on. 
     * 
     * @param sampleSize the number of samples to take
     */
    public void setSampleSize(int sampleSize)
    {
        if(sampleSize >= 0)
        {
            autoSampleSize = false;
            this.sampleSize = sampleSize;
        }
        else 
            autoSampleSize = true;
    }
    
    @Override
    protected double cluster(DataSet data, int[] medioids, int[] assignments)
    {
        int k = medioids.length;
        int[] bestMedoids = new int[medioids.length];
        int[] bestAssignments = new int[assignments.length];
        double bestMedoidsDist = Double.MAX_VALUE;
        
        int sampSize = autoSampleSize ? 40+2*k : sampleSize;
        int[] sampleAssignments = new int[sampSize];
        
        List<DataPoint> sample = new ArrayList<DataPoint>(sampSize);
        /**
         * We need the mapping to be able to go from the sample indicies back to their position in the full data set
         * Key is the sample index [1, 2, 3, ..., sampSize]
         * Value is the coresponding index in the full data set
         */
        Map<Integer, Integer> samplePoints = new Hashtable<Integer, Integer>();
        
        for(int i = 0; i < sampleCount; i++)
        {
            //Take a sample and use PAM on it to get medoids
            samplePoints.clear();
            sample.clear();
            while(samplePoints.size() < sampSize)
            {
                int indx = rand.nextInt(data.getSampleSize());
                if(!samplePoints.containsValue(indx))
                    samplePoints.put(samplePoints.size(), indx);
            }
            for(Integer j : samplePoints.values())
                sample.add(data.getDataPoint(j));
            DataSet sampleSet = new SimpleDataSet(sample);
            
            //Sampling done, now apply PAM
            super.cluster(sampleSet, medioids, sampleAssignments);
            
            //Map the sample medoids back to the full data set
            for(int j = 0; j < medioids.length; j++)
                medioids[j] = samplePoints.get(j);
            
            //Now apply the sample medioids to the full data set
            double sqrdDist = 0.0;
            for(int j = 0; j < data.getSampleSize(); j++)
            {
                double smallestDist = Double.MAX_VALUE;
                int assignment = -1;
                
                for(int z = 0; z < k; z++)
                {
                    double tmp = dm.dist(data.getDataPoint(medioids[z]).getNumericalValues(), data.getDataPoint(j).getNumericalValues());
                    if(tmp < smallestDist)
                    {
                        assignment = z;
                        smallestDist = tmp;
                    }
                }
                assignments[j] = assignment;
                sqrdDist += smallestDist*smallestDist;
            }
            
            if(sqrdDist < bestMedoidsDist)
            {
                bestMedoidsDist = sqrdDist;
                System.arraycopy(medioids, 0, bestMedoids, 0, k);
                System.arraycopy(assignments, 0, bestAssignments, 0, assignments.length);
            }
        }
        
        System.arraycopy(bestMedoids, 0, medioids, 0, k);
        System.arraycopy(bestAssignments, 0, assignments, 0, assignments.length);
        
        return bestMedoidsDist;
    }

    @Override
    public List<List<DataPoint>> cluster(DataSet dataSet, int clusters)
    {
         List<List<DataPoint>> ks = KMeans.getListOfLists(clusters);
        
        int[] clusterIDs = new int[dataSet.getSampleSize()];
        int[] medioids = new int[clusters];
        
        this.cluster(dataSet, medioids, clusterIDs);
        
        for(int i = 0; i < clusterIDs.length; i++)
            ks.get(clusterIDs[i]).add(dataSet.getDataPoint(i));
        
        return ks;
    }
    
    
}
