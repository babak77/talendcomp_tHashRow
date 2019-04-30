package de.cimt.talendcomp.checksum;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Normalization {
	
	private NormalizeConfig config;
	private StringBuilder sb = new StringBuilder();
	private boolean firstCall = true;
	/**
	 * Tracks if all input values are null, regardless if {@link NormalizeConfig#nullReplacement nullReplacement} is set.
	 */
	private boolean allInputsAreNull = true;
	
	/**
	 * Tracks if any input value isn't null. It takes care of {@link NormalizeConfig#nullReplacement nullReplacement}.
	 * If nullReplacment is not null and only one null value is added, than hashBaseHasValues is true!
	 */
	private boolean hashBaseHasValues = false;
	
	public Normalization(NormalizeConfig config) {
		if (config == null)
			throw new IllegalArgumentException("config variable cannot be null");
		
		this.config = config;
		
	}
    
	/**
	 * Reset underlying string builder to add new hash objects 
	 */
	public void reset(){
		firstCall=true;
		allInputsAreNull=true;
		hashBaseHasValues=false;
		sb.setLength(0);
	}
	
    /**
     * Calculates hash value based on added objects
     * @param algorithm -> MD5 / SHA1 / SHA-256
     * @param hashOutputEncoding -> BASE64, HEX
     * @return
     * @throws IllegalArgumentException
     */
    public String calculateHash(String algorithm, HashCalculation.HASH_OUTPUT_ENCODINGS hashOutputEncoding) throws IllegalArgumentException {
    	
    	if(sb.toString().isEmpty() || allInputsAreNull){
    		if(config.isModifyHashOutput())
    			return config.getHashOutputIfBaseIsNull();
    	}
    				
    	if(!"MD5".equalsIgnoreCase(algorithm) && !"SHA1".equalsIgnoreCase(algorithm) && !"SHA-256".equalsIgnoreCase(algorithm))
    		throw new IllegalArgumentException("algorithm has to be MD5, SHA1 or SHA-256");
    	
    	if("MD5".equalsIgnoreCase(algorithm))
    		return HashCalculation.getMD5Hash(this.getNormalizedString(), hashOutputEncoding);
    	
    	if("SHA1".equalsIgnoreCase(algorithm))
    		return HashCalculation.getSHA1Hash(this.getNormalizedString(), hashOutputEncoding);
    	
    	if("SHA-256".equalsIgnoreCase(algorithm))
    		return HashCalculation.getSHA256Hash(this.getNormalizedString(), hashOutputEncoding);

    	return null;
    	
    }
    
    /**
     * Add new objects which will be concatenate with the defined delimiter
     * @param object
     * @param itemConfig
     */
	public void add(Object object, NormalizeObjectConfig itemConfig){
		
		if (firstCall){
			
			if(object != null || config.getNullReplacement() != null) {
				hashBaseHasValues=true;
				sb.append(normalize(object, itemConfig));
			}
				
			
			firstCall = false;
			
		}else{
		
			sb.append(config.getDelimter());
			
			if(object != null) {
				sb.append(normalize(object, itemConfig));
				hashBaseHasValues=true;
			}
		}
		
	}
	
	/**
	 * Returns the normalized string, based on added objects
	 * @return
	 */
	public String getNormalizedString(){
		
		if(!hashBaseHasValues)
			return null;
		
		String normalizedString = sb.toString();
		
		if(config.isCutOffEmptyTrailingObjects()){
			
			boolean endsWithDelimter = normalizedString.endsWith(config.getDelimter());
			boolean endsWithEmptyQuotation = false;
			
			String emptyTrailingQuotation = config.getQuotationCharacter() + config.getQuotationCharacter();
			
			if(config.isQuotingEnabled()){
				endsWithEmptyQuotation = normalizedString.endsWith(emptyTrailingQuotation);
			}
			
			while(endsWithDelimter || endsWithEmptyQuotation){
				
				if(endsWithDelimter)
					normalizedString = normalizedString.substring(0, normalizedString.length() - config.getDelimter().length());
				
				if(endsWithEmptyQuotation)
					normalizedString = normalizedString.substring(0, normalizedString.length() - emptyTrailingQuotation.length());
				
				endsWithDelimter = normalizedString.endsWith(config.getDelimter()); 
				
				if(config.isQuotingEnabled()){
					endsWithEmptyQuotation = normalizedString.endsWith(emptyTrailingQuotation);
				}
				
			}
			
			return normalizedString;
			
		}else{
			return sb.toString();
		}
		
	}

	
	/**
	 * Returns normalized string of this object
	 * @param object
	 * @param itemConfig
	 * @return
	 */
	private String normalize(Object object, NormalizeObjectConfig itemConfig) {
		if (object == null)
			return config.getNullReplacement();
		
		allInputsAreNull = false;
		
		if (object instanceof String)
            return normalize((String) object, itemConfig);

        if (object instanceof Character)
            return normalize((Character) object, itemConfig);

        if (object instanceof Integer)
            return normalize((Integer) object);

        if (object instanceof Long)
            return normalize((Long) object);

        if (object instanceof Short)
            return normalize((Short) object);

        if (object instanceof Float)
            return normalize((Float) object);

        if (object instanceof Double)
            return normalize((Double) object);

        if (object instanceof BigDecimal)
            return normalize((BigDecimal) object);

        if (object instanceof BigInteger)
            return normalize((BigInteger) object);

        if (object instanceof Date)
            return normalize((Date) object);

        if (object instanceof Boolean)
            return normalize((Boolean) object);

//        if (object instanceof Byte)
//            return normalize((Byte) object);
        if (object.getClass().getName().equals("routines.system.Dynamic")) {
            try {
                // handle enterprise feature datatype dynamic
                return handleDynamic(object, itemConfig);
            } catch (Throwable ex) {
                ex.printStackTrace();
                throw new RuntimeException("Issue normalizing Dynamic", ex);
            }
        }

        throw new IllegalArgumentException("Unsupported data type: " + object.getClass());
    }
    
    private String handleDynamic(Object object, NormalizeObjectConfig itemConfig) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{
        final Class  clazz = object.getClass();
        final Method getColumnValue = clazz.getMethod("getColumnValue", new Class[]{ int.class });
        
        
        int count= (int) clazz.getMethod("getColumnCount", new Class[]{}).invoke( object, new Object[]{} );
        
        StringBuilder result = new StringBuilder();

        for(int i=0; i<count; i++){
            if (i>0) 
                result.append(config.getDelimter());

            result.append( normalize( getColumnValue.invoke(object, i) , itemConfig) );

        }
        return result.toString();
    }
    
	
	public String normalize(final Integer value) {
    	return normalizeNumber(value, config.getIntegerFormat());
    }

	public String normalize(final BigInteger value) {
    	return normalizeNumber(value, config.getIntegerFormat());
    }
	
	public String normalize(final String value, final NormalizeObjectConfig itemConfig) {
		
		String normalized = null;
		
        if (value == null) {
        	normalized = config.getNullReplacement();
        } else {
        
        	normalized = value;
        	
        	if(itemConfig.isTrimming()){
        		normalized = value.trim();
        	}
        	
            if (NormalizeObjectConfig.CaseSensitive.UPPER_CASE.equals(itemConfig.getCaseSensitive())){
            	normalized = normalized.toUpperCase();
            }
            
            if (NormalizeObjectConfig.CaseSensitive.LOWER_CASE.equals(itemConfig.getCaseSensitive())){
            	normalized = normalized.toLowerCase();
            }
            
            if(config.isQuotingEnabled()){
            	String quoteChar = config.getQuotationCharacter();
            	normalized = normalized.replace(quoteChar, quoteChar + quoteChar);
            	
            	normalized = quoteChar + normalized + quoteChar;
            }
            
        }

        return normalized;
	}

    public String normalize(final Character value, final NormalizeObjectConfig itemConfig) {
        return normalize(String.valueOf(value), itemConfig);
    }

    public String normalize(final Long value) {
		return normalizeNumber(value, config.getIntegerFormat());
	}
	
	public String normalize(final Float value) {
		return normalizeNumber(value, config.getFloatFormat());
	}

	public String normalize(final Double value) {
		return normalizeNumber(value, config.getDoubleFormat());
	}
	
    public String normalize(final Short value) {
        return normalizeNumber(value, config.getIntegerFormat());
    }

	public String normalize(final BigDecimal value) {
		if (value == null) {
			return config.getNullReplacement();
		}
		
		return value.toPlainString();
	}
	
    public String normalize(final Boolean value) {
        if (value == null) {
            return config.getNullReplacement();
        }

        return String.valueOf(value);
    }

	public String normalize(final Date value) {
		if (value == null) {
			return config.getNullReplacement();
		}
		
		if(config.isDateInMillis()){
			return String.valueOf(value.getTime());
		}else{
			SimpleDateFormat sdf = new SimpleDateFormat(config.getDateFormat());
			return sdf.format(value);
		}
	}

	private String normalizeNumber(final Number value, final NumberFormat nf) {
		String normalized;
    	
        if (value == null) {
            normalized = config.getNullReplacement();
        } else {
            normalized = nf.format(value);
        }

        return normalized;
	}

	
}
