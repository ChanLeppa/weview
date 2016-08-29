package com.weview.control;

import com.weview.control.exceptions.*;
import com.weview.model.UserDataForClient;
import com.weview.model.UserFriendData;
import com.weview.model.loggedinUserHandling.RedisLoggedinUserRepository;
import com.weview.persistence.entities.FriendRequestNotification;
import com.weview.persistence.entities.User;
import com.weview.persistence.UserRepository;
import com.weview.utils.ExceptionInspector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLIntegrityConstraintViolationException;
import java.text.MessageFormat;
import java.util.Date;

@RestController
public class UserRestController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisLoggedinUserRepository loggedInUserRepository;

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String login(@RequestParam String username, @RequestParam String password) {

        User user = userRepository.findByUsername(username);

        if (user == null) {
            throw new UserNotFoundException();
        }

        if (!user.getPassword().equals(password)) {
            throw new InvalidPasswordException();
        }

        loggedInUserRepository.login(username);

        return "redirect: /user/" + username;
    }

    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    public String logout(@RequestParam String username) {

        if (!loggedInUserRepository.isLoggedin(username)) {
            throw new UserNotLoggedInException();
        }

        loggedInUserRepository.logout(username);

        //TODO: Should we destroy the user's player? What if others continue to watch? Unsubscribe with ref count?

        return "redirect: /";
    }

    @RequestMapping(value = "/signup", method = RequestMethod.POST)
    public String signup(@RequestBody User newUser) {

        try {
            userRepository.save(newUser);
            loggedInUserRepository.login(newUser.getUsername());
        }
        catch (DataAccessException e) {
            Throwable cause = ExceptionInspector.getRootCause(e);
            if (cause instanceof SQLIntegrityConstraintViolationException) {
                SQLIntegrityConstraintViolationException sqlConstraint =
                        (SQLIntegrityConstraintViolationException)cause;
                throw new UserFieldConstraintException(newUser, sqlConstraint);
            }
            else {
                throw e;
            }
        }
        catch (Exception e) {
            throw e;
        }

        return "redirect: /user/" + newUser.getUsername();
    }

    @RequestMapping(value = "/user/{username}", method = RequestMethod.GET)
    public UserDataForClient getUserClientData(@PathVariable("username") String username) {

        //TODO: Handle friends
        User theUser = getLoggedInUser(username);
        return getUserDataForClient(theUser);
    }

    @RequestMapping(value = "/user/{username}/friends", method = RequestMethod.GET)
    public UserFriendData getUserFriends(@PathVariable("username") String username) {

        UserFriendData userFriends = new UserFriendData();
        User user = getLoggedInUser(username);

        for (User friend :user.getAllFriends()) {
            UserDataForClient friendData = getUserDataForClient(friend);
            userFriends.getFriends().add(friendData);
        }

        return userFriends;
    }

    private User getLoggedInUser(String username) throws UserNotFoundException, UserNotLoggedInException{
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException();
        }
        if (!loggedInUserRepository.isLoggedin(user.getUsername())) {
            throw new UserNotLoggedInException();
        }

        return user;
    }

    @RequestMapping(value = "/user/{username}/make-friend", method = RequestMethod.POST)
    public String makeFriend(@PathVariable("username") String usernameToBefriend,
                             @RequestParam String username) {
        //TODO: Friend request
        User userRequesting = userRepository.findByUsername(username);
        User userToBefriend = userRepository.findByUsername(usernameToBefriend);

        if (userToBefriend == null || userRequesting == null) {
            throw new UserNotFoundException();
        }

        userRequesting.addFriend(userToBefriend);

        userRepository.save(userRequesting);

        //TODO: Return better argument
        return "Bazinga!!!";
    }

    @RequestMapping(value = "/user/{username}/friend-request", method = RequestMethod.POST)
    public String makeFriendRequest(@PathVariable("username") String usernameRequesting,
                                    @RequestParam String searchParam) {

        User friend = null;
        if ((friend = userRepository.findByUsername(searchParam)) == null &&
                (friend = userRepository.findByUsername(searchParam)) == null) {
            throw new UserNotFoundException();
        }

        notifyUserOfFriendRequest(friend, usernameRequesting);

        //Returns JSON that indicates that the user is logged in
        //so that the requesting client can send him the request via websocket
        Boolean isFriendLoggedIn = loggedInUserRepository.isLoggedin(friend.getUsername());
        String friendLoggedInStatus = "{\"isFriendLoggedIn\" : \"" + isFriendLoggedIn.toString() + "\"}";
        return friendLoggedInStatus;
    }

    private void notifyUserOfFriendRequest(User user, String usernameRequesting) {
        String message = MessageFormat.format("{0} has invited you to be his friend", usernameRequesting);
        Date date = new Date();
        user.addFriendRequest(new FriendRequestNotification(message, date, usernameRequesting));
    }

    private UserDataForClient getUserDataForClient(User friend) {
        String username = friend.getUsername();
        Boolean isLoggedin = loggedInUserRepository.isLoggedin(username);
        return new UserDataForClient(username, friend.getFirstName(), friend.getLastName(), isLoggedin);
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(UserFieldConstraintException.class)
    public UserFieldViolationErrorInfo handleUserFieldException(UserFieldConstraintException ex) {
        return new UserFieldViolationErrorInfo(ex.getException(), ex.getViolatingUser());
    }

}