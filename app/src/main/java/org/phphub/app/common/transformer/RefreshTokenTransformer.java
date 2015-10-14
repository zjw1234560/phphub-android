package org.phphub.app.common.transformer;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.phphub.app.api.entity.element.Token;
import org.phphub.app.common.util.ApiUtils;
import org.phphub.app.model.TokenModel;

import eu.unicate.retroauth.AuthAccountManager;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;

import static org.phphub.app.common.qualifier.AuthType.AUTH_TYPE_REFRESH;

public class RefreshTokenTransformer<T> implements Observable.Transformer<T, T> {
    private TokenModel tokenModel;

    private AuthAccountManager authAccountManager;

    private AccountManager accountManager;

    private Account account;

    private String accountType, tokenType;



    public RefreshTokenTransformer(@NonNull TokenModel tokenModel,
                                   AuthAccountManager authAccountManager,
                                   AccountManager accountManager,
                                   @Nullable Account account,
                                   String accountType,
                                   String tokenType) {
        this.tokenModel = tokenModel;
        this.authAccountManager = authAccountManager;
        this.accountManager = accountManager;
        this.account = account;
        this.accountType = accountType;
        this.tokenType = tokenType;
    }

    @Override
    public Observable<T> call(Observable<T> observable) {
        return observable.retry(new Func2<Integer, Throwable, Boolean>() {
            @Override
            public Boolean call(Integer retryCount, Throwable throwable) {
                final boolean[] needRetry = {false};

                if (retryCount == 1 && ApiUtils.hasUnauthorized(throwable) && account != null) {
                    tokenModel.refreshToken(authAccountManager.getUserData(accountType, AUTH_TYPE_REFRESH))
                            .filter(new Func1<Token, Boolean>() {
                                @Override
                                public Boolean call(Token token) {
                                    return (token != null && !TextUtils.isEmpty(token.getToken()));
                                }
                            })
                            .doOnNext(new Action1<Token>() {
                                @Override
                                public void call(Token token) {
                                    accountManager.setAuthToken(account, tokenType, token.getToken());
                                    accountManager.setUserData(account, AUTH_TYPE_REFRESH, token.getRefreshToken());
                                }
                            })
                            .toBlocking()
                            .forEach(new Action1<Token>() {
                                @Override
                                public void call(Token token) {
                                    needRetry[0] = true;
                                }
                            });
                }

                return needRetry[0];
            }
        });
    }
}