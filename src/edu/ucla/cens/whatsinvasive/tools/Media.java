package edu.ucla.cens.whatsinvasive.tools;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.BitmapFactory.Options;

public class Media {
	public static class Size {
		public int width;
		public int height;
		public boolean exact = false;
		
		public Size(int w, int h){
			this.width = w;
			this.height = h;
		}
	}
	
	public static Bitmap resizeImage(String path, Size size){
		Options options = new Options();
		options.inJustDecodeBounds = true;
    	BitmapFactory.decodeFile(path, options);
    	
		int width = options.outWidth;
        int height = options.outHeight;
		
        options = new Options();
		options.inDither = false;
        
        Boolean scaleByHeight = Math.abs(height - size.height) >= Math.abs(width - size.width);
    	double sampleSize = scaleByHeight ? height / size.height : width / size.width;
    	
    	int inSize = (int) Math.pow(2d, Math.floor(Math.log(sampleSize) / Math.log(2d)));
    	
    	options.inSampleSize = inSize;

    	Bitmap thumb = BitmapFactory.decodeFile(path, options);
    	
    	if(size.exact){
    		// Use a matrix to get the dimensions exactly right
    		float scaleWidth = ((float) size.width) / thumb.getWidth(); 
	        float scaleHeight = ((float) size.height) / thumb.getHeight(); 
	        
	        float scaleBy = Math.min(scaleWidth, scaleHeight);
	        
	        // Create a matrix for the manipulation 
	        Matrix matrix = new Matrix(); 
	        // Resize the bitmap 
	        matrix.postScale(scaleBy, scaleBy); 

	        Bitmap resized = Bitmap.createBitmap(thumb, 0, 0, thumb.getWidth(), thumb.getHeight(), matrix, true);
	        
	        thumb = null;
	        
	        return resized;
    	}else{
    		return thumb;
    	}
	}
}
