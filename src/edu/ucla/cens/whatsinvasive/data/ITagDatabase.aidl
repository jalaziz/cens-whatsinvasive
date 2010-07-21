package edu.ucla.cens.whatsinvasive.data;

import edu.ucla.cens.whatsinvasive.data.ITagDatabaseCallback;

interface ITagDatabase{
	void registerCallback(ITagDatabaseCallback cb);
	void unregisterCallback(ITagDatabaseCallback cb);
}