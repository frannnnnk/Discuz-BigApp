package com.youzu.clan.base.json.favforum;

import java.util.ArrayList;

import com.youzu.clan.base.json.model.PagedVariables;

/**
 * ζΆθηε
 * @author wangxi
 *
 */
public class FavForumVariables extends PagedVariables {
	
	private static final long serialVersionUID = -7952757884359531546L;
	private ArrayList<Forum> list;
	
	@Override
	public ArrayList<Forum> getList() {
		return list;
	}
	public void setList(ArrayList<Forum> list) {
		this.list = list;
	}
	
	
	
}
