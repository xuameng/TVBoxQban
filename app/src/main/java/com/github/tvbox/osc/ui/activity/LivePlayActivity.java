private void initLiveChannelList() {
    List < LiveChannelGroup > list = ApiConfig.get().getChannelGroupList();
    
    // 检查1：list本身是否为空
    boolean isListEmpty = list.isEmpty();
    
    // 检查2：排除"我的收藏"组后，其他组是否全为空
    boolean isAllNonFavoriteGroupsEmpty = true;
    if (!isListEmpty) {
        for (LiveChannelGroup group : list) {
            // 跳过"我的收藏"组，只检查其他组
            if (group != null && 
                !"我的收藏".equals(group.getGroupName()) &&  // 关键：排除收藏组
                group.getLiveChannels() != null && 
                !group.getLiveChannels().isEmpty()) {
                
                // 进一步检查：组中的频道不能是占位符（channelIndex == -1）
                boolean hasValidChannel = false;
                for (LiveChannelItem channel : group.getLiveChannels()) {
                    if (channel != null && channel.getChannelIndex() != -1) {  // 关键：排除占位符
                        hasValidChannel = true;
                        break;
                    }
                }
                
                if (hasValidChannel) {
                    isAllNonFavoriteGroupsEmpty = false;
                    break;
                }
            }
        }
    }
    
    // 如果list为空 或 所有非收藏组都无效/为空，则使用默认列表
    if (isListEmpty || isAllNonFavoriteGroupsEmpty) {
        Log.d("LivePlayActivity", "使用默认列表，原因：listEmpty=" + isListEmpty + 
              ", allNonFavoriteEmpty=" + isAllNonFavoriteGroupsEmpty);
        
        JsonArray live_groups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        if(live_groups.size() > 1) {
            setDefaultLiveChannelList();
            showSuccess();
            App.showToastShort(mContext, "聚汇影视提示您：直播列表为空！请切换线路！");
        } else {
            setDefaultLiveChannelList();
            showSuccess();
            App.showToastShort(mContext, "聚汇影视提示您：频道列表为空！");
        }
        initLiveState(); // 关键：初始化界面
        return;
    }
    
    // 原来的其他逻辑...
    initLiveObj();
    if(list.size() == 1 && list.get(0).getGroupName().startsWith("http://127.0.0.1")) {
        loadProxyLives(list.get(0).getGroupName());
    } else {
        // 其他逻辑...
    }
}
