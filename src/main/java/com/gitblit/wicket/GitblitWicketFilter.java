/*
 * Copyright 2013 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.protocol.http.WicketFilter;
import org.apache.wicket.util.string.Strings;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;

/**
 * 
 * Customization of the WicketFilter to allow smart browser-side caching of
 * some pages.
 * 
 * @author James Moger
 *
 */
public class GitblitWicketFilter extends WicketFilter {
	
	/**
	 * Determines the last-modified date of the requested resource.
	 * 
	 * @param servletRequest
	 * @return The last modified time stamp
	 */
	protected long getLastModified(final HttpServletRequest servletRequest)	{
		final String pathInfo = getRelativePath(servletRequest);
		if (Strings.isEmpty(pathInfo))
			return -1;
		long lastModified = super.getLastModified(servletRequest);
		if (lastModified > -1) {
			return lastModified;
		}
		
		// try to match request against registered CacheControl pages
		String [] paths = pathInfo.split("/");
		
		String page = paths[0];
		String repo = "";
		String commitId = "";
		if (paths.length >= 2) {
			repo = paths[1];
		}
		if (paths.length >= 3) {
			commitId = paths[2];
		}
		
		if (!StringUtils.isEmpty(servletRequest.getParameter("r"))) {
			repo = servletRequest.getParameter("r");
		}
		if (!StringUtils.isEmpty(servletRequest.getParameter("h"))) {
			commitId = servletRequest.getParameter("h");
		}
		
		repo = repo.replace("%2f", "/").replace("%2F", "/").replace(GitBlit.getChar(Keys.web.forwardSlashCharacter, '/'), '/');

		GitBlitWebApp app = (GitBlitWebApp) getWebApplication();
		int expires = GitBlit.getInteger(Keys.web.pageCacheExpires, 0);
		if (!StringUtils.isEmpty(page) && app.isCacheablePage(page) && expires > 0) {
			// page can be cached by the browser
			CacheControl cacheControl = app.getCacheControl(page);
			Date bootDate = GitBlit.getBootDate();
			switch (cacheControl.value()) {
			case ACTIVITY:
				// returns the last activity date of the server
				Date activityDate = GitBlit.getLastActivityDate();
				if (activityDate != null) {
					return activityDate.after(bootDate) ? activityDate.getTime() : bootDate.getTime();
				}
				return bootDate.getTime();
			case BOOT:
				// return the boot date of the server
				return bootDate.getTime();
			case PROJECT:
				// return the latest change date for the project OR the boot date
				ProjectModel project = GitBlit.self().getProjectModel(StringUtils.getRootPath(repo));
				if (project != null) {
					return project.lastChange.after(bootDate) ? project.lastChange.getTime() : bootDate.getTime();
				}
				break;
			case REPOSITORY:
				// return the lastest change date for the repository OR the boot
				// date, whichever is latest
				RepositoryModel repository = GitBlit.self().getRepositoryModel(repo);
				if (repository != null && repository.lastChange != null) {
					return repository.lastChange.after(bootDate) ? repository.lastChange.getTime() : bootDate.getTime();
				}
				break;
			case COMMIT:
				// get the date of the specified commit
				if (StringUtils.isEmpty(commitId)) {
					// no commit id, use boot date
					return bootDate.getTime();
				} else {
					// last modified date is the commit date 
					Repository r = null;
					try {
						// return the timestamp of the associated commit
						r = GitBlit.self().getRepository(repo);
						if (r != null) {
							RevCommit commit = JGitUtils.getCommit(r, commitId);
							if (commit != null) {
								Date date = JGitUtils.getCommitDate(commit);
								return date.after(bootDate) ? date.getTime() : bootDate.getTime();
							}
						}
					} finally {
						if (r != null) {
							r.close();
						}
					}
				}
				break;
			default:
				break;
			}
		}			

		return -1;
	}
}
