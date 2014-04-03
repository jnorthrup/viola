package com.colinalworth.gwt.viola.web.server;

import com.colinalworth.gwt.viola.web.shared.mvp.AcceptsView;
import com.colinalworth.gwt.viola.web.shared.mvp.Place;
import com.colinalworth.gwt.viola.web.shared.mvp.PlaceManager.PlaceBasedPresenterFactory;
import com.colinalworth.gwt.viola.web.shared.mvp.PlaceManager.PlaceFactory;
import com.colinalworth.gwt.viola.web.shared.mvp.Presenter;
import com.colinalworth.gwt.viola.web.shared.mvp.View;
import com.google.inject.Inject;
import one.xio.AsioVisitor.Impl;
import one.xio.HttpHeaders;
import one.xio.HttpMethod;
import one.xio.HttpStatus;
import rxf.server.BlobAntiPatternObject;
import rxf.server.PreRead;
import rxf.server.Rfc822HeaderState;
import rxf.server.Rfc822HeaderState.HttpRequest;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ViolaServerApp extends Impl implements PreRead {
	private static final String APP_RESPONSE_HEAD = "<!doctype html>\n" +
			"<html>\n" +
			"<head>\n" +
			"    <link rel=\"stylesheet\" type=\"text/css\" href=\"/static/viola/reset.css\" />\n" +
			"    <script language='javascript' src='/static/viola/viola.nocache.js'></script>\n" +
			"</head>\n" +
			"<body>\n" +
			"<noscript>";
	private static final String APP_RESPONSE_TAIL = "\n" +
			"</noscript>\n" +
			"</body>\n" +
			"</html>";
	@Inject
	PlaceFactory placeFactory;
	@Inject
	PlaceBasedPresenterFactory presenterFactory;
	@Override
	public void onRead(final SelectionKey key) throws Exception {
		ByteBuffer cursor1 = null;
		HttpRequest req1 = null;
		if (key.attachment() instanceof Object[]) {
			Object[] ar = (Object[]) key.attachment();
			for (Object o : ar) {
				if (o instanceof ByteBuffer) {
					cursor1 = (ByteBuffer) o;
				} else if (o instanceof Rfc822HeaderState) {
					req1 = ((Rfc822HeaderState) o).$req();
				}
			}
		}
		final HttpRequest request = req1;
		final ByteBuffer cursor = cursor1;
		if (request == null || cursor == null) {
			Errors.$500(key);
			return;//fail, something miswired
		}
		final String path = request.path();

		//peel off the leading / since the client never has it
		final Place place = placeFactory.route(path.substring(1));
		if (place == null) {
			//either we're looking at a 404, or a static file
			//assume a static file, if we can't find it, a 404 will be issued
			//TODO actually look for a static file

			Errors.$404(key, path);
			return;
		}
		final Presenter presenter = presenterFactory.getPresenterInstance(place);
		if (presenter == null) {
			Errors.$404(key, path);
			return;
		}

		//push the rest of this off into a submitted task, signal ready for write when done, write contents
		BlobAntiPatternObject.EXECUTOR_SERVICE.submit(new Runnable() {
			public void run() {
				final View<?>[] viewWrapper = new View[1];
				presenter.go(new AcceptsView() {
					@Override
					public void setView(View view) {
						viewWrapper[0] = view;
					}
				}, place);

				final String response = viewWrapper[0] == null ? null :
						viewWrapper[0].asSafeHtml() == null ? null :
						APP_RESPONSE_HEAD + viewWrapper[0].asSafeHtml().asString() + APP_RESPONSE_TAIL;
				if (response == null) {
					// assume that if it returns null that it already sent back a response
					return;
				}
				key.attach(new Impl(){
					@Override
					public void onWrite(SelectionKey key) throws Exception {
						ByteBuffer resp = new Rfc822HeaderState().$res()
								.resCode(HttpStatus.$200)
								.headerString(HttpHeaders.Content$2dType, "text/html")
								.headerString(HttpHeaders.Content$2dLength, String.valueOf(response.length()))
								.as(ByteBuffer.class);

						((SocketChannel) key.channel()).write(resp);
						((SocketChannel) key.channel()).write(HttpMethod.UTF8.encode(response));
						key.selector().wakeup();
						key.interestOps(SelectionKey.OP_READ).attach(null);
					}
				});
				key.interestOps(SelectionKey.OP_WRITE);
			}
		});
	}
}