package com.colinalworth.gwt.viola.web.server;

import com.colinalworth.gwt.viola.web.client.impl.AbstractPlacesImpl;
import com.colinalworth.gwt.viola.web.shared.mvp.CreateProjectPresenter.CreateProjectPlace;
import com.colinalworth.gwt.viola.web.shared.mvp.ExamplePresenter.ExamplePlace;
import com.colinalworth.gwt.viola.web.shared.mvp.HomePresenter.HomePlace;
import com.colinalworth.gwt.viola.web.shared.mvp.Place;
import com.colinalworth.gwt.viola.web.shared.mvp.ProjectEditorPresenter.ProjectEditorPlace;
import com.colinalworth.gwt.viola.web.shared.mvp.SearchPresenter.SearchPlace;
import com.colinalworth.gwt.viola.web.shared.mvp.ViolaPlaces;
import com.google.gwt.regexp.shared.MatchResult;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.safehtml.shared.UriUtils;
import com.google.web.bindery.autobean.shared.AutoBean;
import com.google.web.bindery.autobean.shared.AutoBeanFactory;
import com.google.web.bindery.autobean.vm.AutoBeanFactorySource;

public class ViolaPlaces_ServerImpl extends AbstractPlacesImpl implements ViolaPlaces {

	interface ABF extends AutoBeanFactory {
		AutoBean<ExamplePlace> example();
		AutoBean<SearchPlace> search();
		AutoBean<CreateProjectPlace> createProject();
		AutoBean<ProjectEditorPlace> editProject();
		AutoBean<HomePlace> home();
	}

	String validateRegex = "^(?:example/(?:[a-zA-Z0-9%]*)/)|" +
			"(?:search/\\?q=(?:[a-zA-Z0-9%]*))|" +
			"(?:proj/new)|" +
			"(?:proj/([a-zA-Z0-9%]+)(?:/([a-zA-Z0-9%./]*))?)|" +
			"(?:)$";
	RegExp example = RegExp.compile("^example/([a-zA-Z0-9%]*)/$");
	RegExp search = RegExp.compile("^search/\\?q=([a-zA-Z0-9%]*)$");
	RegExp createProject = RegExp.compile("^proj/new$");
	RegExp editProject = RegExp.compile("^proj/([a-zA-Z0-9%]+)(?:/([a-zA-Z0-9%./]*))?$");
	RegExp home = RegExp.compile("^$");

	public ViolaPlaces_ServerImpl() {
		super(AutoBeanFactorySource.create(ABF.class));
	}

	@Override
	public ExamplePlace example() {
		return create(ExamplePlace.class);
	}

	@Override
	public SearchPlace search() {
		return create(SearchPlace.class);
	}

	@Override
	public HomePlace home() {
		return create(HomePlace.class);
	}

	@Override
	public CreateProjectPlace createProject() {
		return create(CreateProjectPlace.class);
	}

	@Override
	public ProjectEditorPlace editProject() {
		return create(ProjectEditorPlace.class);
	}

	@Override
	protected String innerRoute(Place place) {
		if (place == null) {
			return null;
		}
		if (place instanceof SearchPlace) {
			return "search/?q=" + UriUtils.encode(((SearchPlace) place).getQuery()) + "";
		}
		if (place instanceof ExamplePlace) {
			return "example/" + UriUtils.encode(((ExamplePlace) place).getId()) + "/";
		}
		if (place instanceof CreateProjectPlace) {
			return "proj/new";
		}
		if (place instanceof ProjectEditorPlace) {
			ProjectEditorPlace projectEditorPlace = (ProjectEditorPlace) place;
			return "proj/" + UriUtils.encode(projectEditorPlace.getId()) + "/" + (projectEditorPlace.getActiveFile() == null ? "" : UriUtils.encode(projectEditorPlace.getActiveFile()));
		}
		if (place instanceof HomePlace) {
			return "";
		}
		assert false : "Unsupported place type " + place.getClass();
		return null;
	}

	@Override
	protected Place innerRoute(String url) {
		if (search.test(url)) {
			SearchPlace s = search();
			MatchResult res = search.exec(url);
			//TODO url decode
			s.setQuery(res.getGroup(1));
			return s;
		}
		if (example.test(url)) {
			ExamplePlace s = example();
			MatchResult res = example.exec(url);
			//TODO url decode
			s.setId(res.getGroup(1));
			return s;
		}
		if (createProject.test(url)) {
			CreateProjectPlace s = createProject();
			return s;
		}
		if (editProject.test(url)) {
			ProjectEditorPlace s = editProject();
			MatchResult res = editProject.exec(url);
			s.setId(res.getGroup(1));
			if (res.getGroupCount() > 2) {
				s.setActiveFile(res.getGroup(2));
			}
			return s;
		}
		if (home.test(url)) {
			HomePlace s = home();
			return s;
		}
		return null;
	}

	@Override
	protected boolean verifyValid(String url) {
		return url.matches(validateRegex);
	}
}
