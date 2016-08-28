package com.weview.model.player.playerdb;

import java.util.Collection;


/*Should we split into PlayerRepo and PlayerSubscriberRepo?!?!?*/
public interface PlayerRepository {

    void addPlayer(String playerID, PlayerSynchronizationData playerData/*, SyncBean or other player representation*/);

    void removePlayer(String playerID);

    PlayerSynchronizationData getPlayerData(String playerID);

    void addSubscriber(String playerID, PlayerSubscriberData subscriber);

    void removeSubscriber(String playerID, String subscriberID);

    Collection<PlayerSubscriberData> getSubscribers(String playerID);

    PlayerSubscriberData getSubscriber(String playerID, String subscriberID);

    Boolean allSubscribersCanPlay(String playerID);

    Boolean doesPlayerExist(String playerID);

    Boolean isSubscriber(String playerID, String subscriberID);

    void updateSubscriber(String playerID, PlayerSubscriberData subscriber);
}