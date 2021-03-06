package com.colinalworth.gwt.viola.web.server;

import one.xio.AsioVisitor.Impl;
import one.xio.HttpStatus;
import one.xio.MimeType;
import rxf.core.CouchNamespace;
import rxf.core.DateHeaderParser;
import rxf.core.Rfc822HeaderState;
import rxf.shared.CompressionTypes;
import rxf.shared.PreRead;
import rxf.web.inf.ProtocolMethodDispatch;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.min;
import static java.nio.channels.SelectionKey.*;
import static one.xio.HttpHeaders.*;
import static rxf.core.Rfc822HeaderState.*;
 @PreRead
public class ContentRootImpl extends Impl{

	public static final String SLASHDOTSLASH = File.separator + "." + File.separator;
	public static final String DOUBLESEP = File.separator + File.separator;
	private final String rootPath;
	private final Pattern passthroughExpr;

	public ContentRootImpl() {
		this(CouchNamespace.COUCH_DEFAULT_FS_ROOT, null);
	}

	public ContentRootImpl(String rootPath, Pattern pattern) {
		this.rootPath = rootPath;
		this.passthroughExpr = pattern;
		File dir = new File(rootPath);
		if (!dir.isDirectory() && dir.canRead())
			throw new IllegalAccessError("can't verify readable dir at " + rootPath);
	}



	private static String fileScrub(String scrubMe) {
		final char inverseChar = '/' == File.separatorChar ? '\\' : '/';
		return null == scrubMe ? null : scrubMe.trim().replace(inverseChar, File.separatorChar)
				.replace(DOUBLESEP, "" + File.separator).replace("..", ".");
	}

	@Override
	public void onRead(SelectionKey key) throws Exception {
		final SocketChannel channel = (SocketChannel) key.channel();
		ByteBuffer cursor = null;
		if (key.attachment() instanceof Object[]) {
			Object[] ar = (Object[]) key.attachment();
			for (Object o : ar) {
				if (o instanceof ByteBuffer) {
					cursor = (ByteBuffer) o;
					continue;
				}
			}
		}
		key.attach(this);
		cursor =
				null == cursor ? ByteBuffer.allocateDirect(4<<10) : cursor.hasRemaining()
						? cursor : ByteBuffer.allocateDirect(cursor.capacity() << 1).put(
						(ByteBuffer) cursor.rewind());
		int read = channel.read(cursor);
		if (read == -1)
			key.cancel();
		Buffer flip = cursor.duplicate().flip();

		final HttpRequest req =
				(HttpRequest) new Rfc822HeaderState().addHeaderInterest(Accept$2dEncoding,
						If$2dModified$2dSince, If$2dUnmodified$2dSince).$req().read((ByteBuffer) flip);
		if (!
				suffixMatchChunks(ProtocolMethodDispatch.HEADER_TERMINATOR, req.headerBuf())) {
			return;
		}
		cursor = ((ByteBuffer) flip).slice();
		key.attach(new Impl() {
			public void onWrite(SelectionKey key) throws Exception {

				String requestedPath = req.path().split("\\?")[0];
				if (passthroughExpr != null) {
					Matcher m = passthroughExpr.matcher(requestedPath);
					if (!m.matches()) {
						Errors.$500(key);
						return;
					}
					requestedPath = m.group(1);
				}
				String finalFname = fileScrub(rootPath + SLASHDOTSLASH + requestedPath);
				File file = new File(finalFname);
				if (file.isDirectory()) {
					file = new File((finalFname + "/index.html"));
				}
				finalFname = (file.getCanonicalPath());
				file = new File(finalFname);

				java.util.Date fdate = new java.util.Date(file.lastModified());

				String since = req.headerString(If$2dModified$2dSince);
				String accepts = req.headerString(Accept$2dEncoding);

				HttpResponse res = req.$res();
				if (null != since) {
					java.util.Date cachedDate = DateHeaderParser.parseDate(since);

					if (cachedDate.after(fdate)) {

						res.status(HttpStatus.$304).headerString(Connection, "close").headerString(Last$2dModified,
								DateHeaderParser.formatHttpHeaderDate(fdate));
						int write = channel.write(res.as(ByteBuffer.class));
						key.interestOps(OP_READ).attach(null);
						return;
					}
				} else {
					since = req.headerString(If$2dUnmodified$2dSince);

					if (null != since) {
						java.util.Date cachedDate = DateHeaderParser.parseDate(since);

						if (cachedDate.before(fdate)) {

							res.status(HttpStatus.$412).headerString(Connection, "close").headerString(
									Last$2dModified, DateHeaderParser.formatHttpHeaderDate(fdate));
							int write = channel.write(res.as(ByteBuffer.class));
							key.interestOps(OP_READ).attach(null);
							return;
						}
					}
				}
				String ceString = null;
				if (null != accepts) {

					for (CompressionTypes compType : CompressionTypes.values()) {
						if (accepts.contains(compType.name())) {
							File f = new File(file.getAbsoluteFile() + "." + compType.suffix);
							if (f.isFile() && f.canRead()) {
								if (rxf.core.Server.DEBUG_SENDJSON) {
									System.err.println("sending compressed archive: " + f.getAbsolutePath());
								}
								ceString = (compType.name());
								file = f;
								break;
							}
						}
					}
				}
				boolean send200 = file.canRead() && file.isFile();

				if (send200) {
					final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
					final long total = randomAccessFile.length();
					final FileChannel fileChannel = randomAccessFile.getChannel();

					String substring = finalFname.substring(finalFname.lastIndexOf('.') + 1);
					MimeType mimeType = MimeType.valueOf(substring);
					long length = randomAccessFile.length();

					res.status(HttpStatus.$200).headerString(Content$2dType,
							((null == mimeType) ? MimeType.bin : mimeType).contentType).headerString(
							Content$2dLength, String.valueOf(length)).headerString(Connection, "close").headerString(
							Date, DateHeaderParser.formatHttpHeaderDate(fdate));;
					if (null != ceString)
						res.headerString(Content$2dEncoding, ceString);
					ByteBuffer response = res.as(ByteBuffer.class);
					int write = channel.write(response);
					final int sendBufferSize = 4<<10;
					final long[] progress = {fileChannel.transferTo(0, sendBufferSize, channel)};
					key.interestOps(OP_WRITE | OP_CONNECT);
					key.selector().wakeup();
					key.attach(new Impl() {

						public void onWrite(SelectionKey key) throws Exception {
							long remaining = total - progress[0];
							progress[0] +=
									fileChannel.transferTo(progress[0], min(sendBufferSize, remaining), channel);
							remaining = total - progress[0];
							if (0 == remaining) {
								fileChannel.close();
								randomAccessFile.close();
								key.selector().wakeup();
								key.interestOps(OP_READ).attach(null);
							}
						}
					});
				} else {
					key.selector().wakeup();
					key.interestOps(OP_WRITE).attach(new Impl() {

						public void onWrite(SelectionKey key) throws Exception {
							int write =
									channel.write(req.$res().status(HttpStatus.$404).headerString(Content$2dLength, "0")
											.as(ByteBuffer.class));
							key.selector().wakeup();
							key.interestOps(OP_READ).attach(null);
						}
					});
				}
			}
		});
		key.interestOps(SelectionKey.OP_WRITE);
	}

}
