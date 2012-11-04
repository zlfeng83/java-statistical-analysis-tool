
package jsat.datatransform;

import jsat.DataSet;
import jsat.classifiers.CategoricalData;
import jsat.classifiers.DataPoint;
import jsat.linear.*;

/**
 *
 * @author Edward Raff
 */
public class NominalToNumeric implements DataTransform
{
    private final int origNumericalCount;
    private final CategoricalData[] categoricalData;
    private int addedNumers;
    
    public NominalToNumeric(DataSet dataSet)
    {
        this(dataSet.getNumNumericalVars(), dataSet.getCategories());
    }

    public NominalToNumeric(int origNumericalCount, CategoricalData[] categoricalData)
    {
        this.origNumericalCount = origNumericalCount;
        this.categoricalData = categoricalData;
        addedNumers = 0;
        
        for(CategoricalData cd : categoricalData)
            addedNumers += cd.getNumOfCategories();
                
    }
    
    @Override
    public DataPoint transform(DataPoint dp)
    {
        Vec v;
        
        //TODO we should detect if there are going to be so many sparce spaces added by the categorical data that we should just choose a sparce vector anyway
        if(dp.getNumericalValues().isSparse())
            v = new SparseVector(origNumericalCount+addedNumers);
        else
            v = new DenseVector(origNumericalCount+addedNumers);
        
        
        Vec oldV = dp.getNumericalValues();
        int i = 0;
        for(i = 0; i < origNumericalCount; i++)
            v.set(i, oldV.get(i)); 
        for(int j =0; j < categoricalData.length; j++)
        {
            v.set(i+dp.getCategoricalValue(j), 1.0);
            i += categoricalData[j].getNumOfCategories();
        }
        
        return new DataPoint(v, new int[0], new CategoricalData[0]);
    }

    @Override
    public DataTransform clone()
    {
        return new NominalToNumeric(origNumericalCount, categoricalData);
    }
    
    static public class NominalToNumericTransformFactory implements DataTransformFactory
    {

        public NominalToNumericTransformFactory()
        {
        }
        
        @Override
        public DataTransform getTransform(DataSet dataset)
        {
            return new NominalToNumeric(dataset);
        }
    }
}
