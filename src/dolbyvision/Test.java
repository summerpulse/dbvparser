package dolbyvision;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

class DolbyVisionParser {
	public void setDataSource(String path) {
		this.mPath = path;
	}
	public void initialize() throws Exception {
//		mMatcher = new KMPMatcher();
		out = new FileOutputStream("test.out").getChannel();
		File f = new File(mPath);
		mFileSize = f.length();
		mFileSizeLeft = mFileSize;
		mFileSizeRead = 0;
		mInputStream = new FileInputStream(f);

		long resonableSize = howMuchShouldRead(1024*1024);
		mBuffer = IOUtils.toByteArray(mInputStream, resonableSize);
		mFileSizeLeft -= resonableSize;
		mFileSizeRead += resonableSize;
		
		if (mFileSizeLeft == 0)
			mEOS = true;
		else
			mEOS = false;
		mAccuOutputSize = 0;
	}

	public void close() {
		try
		{
			out.close();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public ByteBuffer read() throws IOException {
//		byte[] data = null;
		ByteBuffer bb =	null;

		if (mEOS)
			return bb;
		System.out.println("mBuffer.length:"+mBuffer.length);
		int startCodeIdx1=-1;
		int startCodeIdx2=-1;

		boolean gotELAUTAG = false;
		boolean gotRPUTAG = false;
		
//		try {
//			data = IOUtils.toByteArray(mInputStream, 1024*1024);
//			if (data.length < 1024 * 1024)
//				mEOS = true;
//		}
//		catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}

		for(int i=0;i<mBuffer.length;i++) {
			if ((mBuffer[i]  ==0x0) &&
				(mBuffer[i+1]==0x0) &&
				(mBuffer[i+2]==0x0) &&
				(mBuffer[i+3]==0x01) &&
				( ((mBuffer[i+4]!=0x7e && mBuffer[i+4]!=0x7c)) || mBuffer[i+5]!=0x01)) {
				// Log.d(TAG, "Find startCodec at "+i);

				//System.out.println("found startCode at "+Integer.toHexString(i + mAccuOutputSize));

				if (startCodeIdx1 == -1)
					startCodeIdx1 = i;
				else if (startCodeIdx2 == -1 || !(gotELAUTAG && gotRPUTAG))
					startCodeIdx2 = i;

				if (gotELAUTAG && gotRPUTAG) {
					int copiedSize = 0;
					byte[] subData = null;

					if (startCodeIdx1 != -1 && startCodeIdx2 != -1 ) {
						subData = Arrays.copyOfRange(mBuffer, startCodeIdx1, startCodeIdx2);
						//System.out.println("startCodeIdx1 to startCodeIdx2");
					} else if (startCodeIdx1 != -1 && startCodeIdx2 == -1 && mEOS) {
						subData = Arrays.copyOfRange(mBuffer, startCodeIdx1, mBuffer.length-1);
						//System.out.println("startCodeIdx1 to end");
					} else {
						System.out.println("startCodeIdx1="+startCodeIdx1+", startCodeIdx2="+startCodeIdx2);
						
						if (startCodeIdx1 != -1 && startCodeIdx2 == -1) {
							System.out.println("need more data for parse");
							byte[] suplementBuffer = null;
							if (mFileSizeLeft <= 1024*124) {
								suplementBuffer = IOUtils.toByteArray(mInputStream, mFileSizeLeft);
								mFileSizeRead += mFileSizeLeft;
								mFileSizeLeft -= mFileSizeLeft;
								mEOS = true;
							} else {
								suplementBuffer = IOUtils.toByteArray(mInputStream, 1024*124);
								mFileSizeRead += 1024*124;
								mFileSizeLeft -= 1024*124;
								mEOS = false;
							}
							
							mBuffer = ArrayUtils.addAll(mBuffer, suplementBuffer);
							continue;
						} else
							System.out.println("malformed stream");
						
					}

					bb = ByteBuffer.wrap(subData);
					out.write(bb);
					copiedSize = subData.length;
					mAccuOutputSize += copiedSize;

					byte[] leftOver = Arrays.copyOfRange(mBuffer, startCodeIdx2, mBuffer.length);
					byte[] suplementBuffer = null;
					
					if (mFileSizeLeft <= copiedSize) {
						suplementBuffer = IOUtils.toByteArray(mInputStream, mFileSizeLeft);
						mFileSizeRead += mFileSizeLeft;
						mFileSizeLeft -= mFileSizeLeft;
						mEOS = true;
					} else {
						suplementBuffer = IOUtils.toByteArray(mInputStream, copiedSize);
						mFileSizeRead += copiedSize;
						mFileSizeLeft -= copiedSize;
						mEOS = false;
					}
					mBuffer = ArrayUtils.addAll(leftOver, suplementBuffer);
					break;
				} else
					continue;
			}

			if (mBuffer[i]  ==0x0 &&
				mBuffer[i+1]==0x0 &&
				mBuffer[i+2]==0x0 &&
				mBuffer[i+3]==0x01 &&
				mBuffer[i+4]==0x7e &&
				mBuffer[i+5]==0x01) {
				gotELAUTAG = true;
				//Log.d(TAG, "Find ELAUTAG at "+i);
				continue;
			}

			if (mBuffer[i]  ==0x0 &&
				mBuffer[i+1]==0x0 &&
				mBuffer[i+2]==0x0 &&
				mBuffer[i+3]==0x01 &&
				mBuffer[i+4]==0x7c &&
				mBuffer[i+5]==0x01) {
				gotRPUTAG = true;
				//Log.d(TAG, "Find RPUTAG at "+i);
				continue;
			}
		}
		return bb;
	}

	private long howMuchShouldRead(long requestedSize) {
		if (mFileSizeLeft < 1024 * 1024)
			return mFileSizeLeft;
		else
			return 1024 * 1024;
	}

	public static byte[] STARTCODE = {0x00, 0x00, 0x00, 0x01};
	public static byte[] ELAUTAG   = {0x00, 0x00, 0x00, 0x01, 0x7E, 0x01};
	public static byte[] RPUTAG    = {0x00, 0x00, 0x00, 0x01, 0x7C, 0x01};
	private String mPath;
	private boolean mEOS;
	private long mFileSize;
	private long mFileSizeRead;
	private long mFileSizeLeft;
	private byte[] mBuffer;
	private int mAccuOutputSize;
	private FileChannel out;
//	private byte[] data;
//	private byte[] mStream2;
	private InputStream mInputStream;
//	private KMPMatcher mMatcher;
}

public class Test {
	public static void main(String[] args) throws Exception {
		DolbyVisionParser dvp = new DolbyVisionParser();

		dvp.setDataSource("Teststream_24fps_3840x2160_10000kbps_hevc+2000kbps_hevc_0_91_ves.265");

		dvp.initialize();

		ByteBuffer bb = null;
		while((bb = dvp.read()) != null) {
			System.out.println("bb.size:"+bb.position());
		}

	}
}