package com.example.roma.translater2ndrxroom.transl.Translate;

import android.content.Context;
import android.net.NetworkInfo;
import android.util.Log;

import com.example.roma.translater2ndrxroom.data.TranslateItem;
import com.example.roma.translater2ndrxroom.data.source.Repository;
import com.example.roma.translater2ndrxroom.util.NetworkConnection;

import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;


public class TranslatePresenter implements TranslateContract.Presenter {

    Context context;

    private Repository repository;

    private TranslateContract.View view;

    private boolean lang_en_ru = true;

    TranslateItem item;

    CompositeDisposable disposableAll = new CompositeDisposable();
    CompositeDisposable disposableSearch = new CompositeDisposable();


    public TranslatePresenter(Repository repository, TranslateContract.View view, Context context) {
        this.repository = repository;
        this.view = view;
        this.context = context;

    }

    @Override
    public void start() {

    }

    @Override
    public void subscribe() {

    }

    @Override
    public void unsubscribe() {
        disposableSearch.clear();
    }

    @Override
    public void switcherClick() {
        if (lang_en_ru) {
            view.showAnimSwitch(true);
            lang_en_ru = false;
        } else {
            view.showAnimSwitch(false);
            lang_en_ru = true;
        }
        Log.v("lang", lang_en_ru+" ");
    }

    @Override
    public void deleteSearch() {
        view.clearSearch();
        view.hideClearButton();
        view.unFocusSearchEdit();
    }


    @Override
    public void searchTranslate(final String wordIn) {
        if (NetworkConnection.isLostConnection(context)){
            view.showErrorMessage();
            return;
        }

        switchViewsState(false);
        if (wordIn.length() == 0) {
            view.hideProgress();
            disposableSearch.clear();
            return;
        }

        view.showClearButton();
        view.setWordIn(wordIn);

        disposableSearch.clear();

        Maybe<TranslateItem> search = Maybe
                .concat(
                        repository.checkItem(wordIn),
                        repository.searchRemote(wordIn, lang_en_ru ? "en-ru" : "ru-en")
                                .toMaybe())
                .firstElement();

        Disposable searchDispos = search
                .delay(1000, TimeUnit.MILLISECONDS)
                .doOnSuccess(new Consumer<TranslateItem>() {
                    @Override
                    public void accept(@NonNull TranslateItem translateItem) throws Exception {
                        repository.insert(new TranslateItem(
                                translateItem.getWordIn(),
                                translateItem.getWordOut(),
                                translateItem.getLangIn(),
                                translateItem.isFavorite())
                        );
                    }
                })
                .flatMapSingle(new Function<TranslateItem, SingleSource<TranslateItem>>() {
                    @Override
                    public SingleSource<TranslateItem> apply(@NonNull TranslateItem translateItem) throws Exception {
                        return Single.just(translateItem);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doAfterTerminate(new Action() {
                    @Override
                    public void run() throws Exception {
                        switchViewsState(true);
                    }
                })
                .subscribeWith(new DisposableSingleObserver<TranslateItem>() {
                    @Override
                    public void onSuccess(@NonNull TranslateItem translateItem) {
                        item = translateItem;
                        view.setWordIn(translateItem.getWordIn());
                        view.setWordOut(translateItem.getWordOut());
                        view.setStateFavourite(translateItem.isFavorite());
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        e.printStackTrace();
                    }
                });
        disposableSearch.add(searchDispos);
    }

    private void switchViewsState(boolean stateShow) {
        if (stateShow) {
            view.hideErrorMessage();
            view.hideProgress();
            view.showWords();
            view.showFavourite();
        } else {
            view.hideErrorMessage();
            view.showProgress();
            view.hideFavourite();
            view.hideWords();
        }

    }

    @Override
    public void setFavorite() {
        if (item.isFavorite())
            item.setFavorite(false);
        else item.setFavorite(true);
        Completable.fromAction(new Action() {
            @Override
            public void run() throws Exception {
                repository.setUnsetFavoutite(item);
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableCompletableObserver() {
                    @Override
                    public void onComplete() {
                        if (item.isFavorite())
                            view.setStateFavourite(true);
                        else view.setStateFavourite(false);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {

                    }
                });

    }
}
