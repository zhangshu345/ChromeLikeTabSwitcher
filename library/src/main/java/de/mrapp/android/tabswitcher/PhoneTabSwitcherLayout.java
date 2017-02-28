/*
 * Copyright 2016 - 2017 Michael Rapp
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package de.mrapp.android.tabswitcher;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.FrameLayout;

import java.util.Collections;

import de.mrapp.android.tabswitcher.arithmetic.Arithmetics;
import de.mrapp.android.tabswitcher.model.AnimationType;
import de.mrapp.android.tabswitcher.model.Axis;
import de.mrapp.android.tabswitcher.iterator.Iterator;
import de.mrapp.android.tabswitcher.model.Layout;
import de.mrapp.android.tabswitcher.model.State;
import de.mrapp.android.tabswitcher.model.TabItem;
import de.mrapp.android.tabswitcher.model.Tag;
import de.mrapp.android.tabswitcher.util.ViewRecycler;
import de.mrapp.android.tabswitcher.view.ChildViewRecycler;
import de.mrapp.android.tabswitcher.view.RecyclerAdapter;
import de.mrapp.android.tabswitcher.view.TabViewHolder;
import de.mrapp.android.util.DisplayUtil.Orientation;
import de.mrapp.android.util.ViewUtil;

import static de.mrapp.android.util.Condition.ensureNotNull;
import static de.mrapp.android.util.DisplayUtil.getOrientation;

/**
 * A layout, which implements the functionality of a {@link TabSwitcher} on smartphones.
 *
 * @author Michael Rapp
 * @since 1.0.0
 */
public class PhoneTabSwitcherLayout extends AbstractTabSwitcherLayout
        implements DragHandler.Callback {

    /**
     * An animation, which allows to fling the tabs.
     */
    private class FlingAnimation extends Animation {

        /**
         * The distance, the tabs should be moved.
         */
        private final float distance;

        /**
         * Creates a new fling animation.
         *
         * @param distance
         *         The distance, the tabs should be moved, in pixels as a {@link Float} value
         */
        public FlingAnimation(final float distance) {
            this.distance = distance;
        }

        @Override
        protected void applyTransformation(final float interpolatedTime, final Transformation t) {
            if (flingAnimation != null) {
                dragHandler.handleDrag(distance * interpolatedTime, 0);
            }
        }

    }

    /**
     * The arithmetics, which are used to calculate the positions, size and rotation of tabs.
     */
    private final Arithmetics arithmetics;

    /**
     * The inset of tabs in pixels.
     */
    private final int tabInset;

    /**
     * The width of the border, which is drawn around the preview of tabs.
     */
    private final int tabBorderWidth;

    /**
     * The number of tabs, which are contained by a stack.
     */
    private final int stackedTabCount;

    /**
     * The space between tabs, which are part of a stack, in pixels.
     */
    private final int stackedTabSpacing;

    /**
     * The maximum space between neighboring tabs in pixels.
     */
    private final int maxTabSpacing;

    /**
     * The alpha of a tab, when it is swiped.
     */
    private final float swipedTabAlpha;

    /**
     * The scale of a tab, when it is swiped.
     */
    private final float swipedTabScale;

    /**
     * The duration of the animation, which is used to show the switcher.
     */
    private final long showSwitcherAnimationDuration;

    /**
     * The duration of the animation, which is used to hide the switcher.
     */
    private final long hideSwitcherAnimationDuration;

    /**
     * The duration of the animation, which is used to show or hide the toolbar.
     */
    private final long toolbarVisibilityAnimationDuration;

    /**
     * The delay of the animation, which is used to show or hide the toolbar.
     */
    private final long toolbarVisibilityAnimationDelay;

    /**
     * The duration of the animation, which is used to swipe tabs.
     */
    private final long swipeAnimationDuration;

    /**
     * The delay of the animation, which is used to remove all tabs.
     */
    private final long clearAnimationDelay;

    /**
     * The duration of the animation, which is used to relocate tabs.
     */
    private final long relocateAnimationDuration;

    /**
     * The delay of the animation, which is used to relocate tabs.
     */
    private final long relocateAnimationDelay;

    /**
     * The duration of the animation, which is used to revert overshoots.
     */
    private final long revertOvershootAnimationDuration;

    /**
     * The view recycler, which allows to recycler the child views of tabs.
     */
    private ChildViewRecycler childViewRecycler;

    /**
     * The adapter, which allows to inflate the views, which are used to visualize tabs.
     */
    private RecyclerAdapter recyclerAdapter;

    /**
     * The view recycler, which allows to recycle the views, which are used to visualize tabs.
     */
    private ViewRecycler<TabItem, Integer> viewRecycler;

    /**
     * The drag hander, which is used to calculate the positions of tabs.
     */
    private DragHandler dragHandler;

    /**
     * The view group, which contains the tab switcher's tabs.
     */
    private ViewGroup tabContainer;

    /**
     * The toolbar, which is shown, when the tab switcher is shown.
     */
    private Toolbar toolbar;

    /**
     * The bottom margin of a view, which visualizes a tab.
     */
    private int tabViewBottomMargin;

    /**
     * The animation, which is used to show or hide the toolbar.
     */
    private ViewPropertyAnimator toolbarAnimation;

    /**
     * The animation, which is used to fling the tabs.
     */
    private Animation flingAnimation;

    /**
     * Calculates and returns the bottom margin of a view, which visualizes a tab.
     *
     * @param view
     *         The view, whose bottom margin should be calculated, as an instance of the class
     *         {@link View}. The view may not be null
     * @return The bottom margin, which has been calculated, in pixels as an {@link Integer} value
     */
    private int calculateBottomMargin(@NonNull final View view) {
        Axis axis =
                getLayout() == Layout.PHONE_LANDSCAPE ? Axis.ORTHOGONAL_AXIS : Axis.DRAGGING_AXIS;
        float tabHeight = (view.getHeight() - 2 * tabInset) * arithmetics.getScale(view, true);
        float containerHeight = arithmetics.getSize(axis, tabContainer);
        int toolbarHeight = isToolbarShown() ? toolbar.getHeight() - tabInset : 0;
        int stackHeight =
                getLayout() == Layout.PHONE_LANDSCAPE ? 0 : stackedTabCount * stackedTabSpacing;
        return Math.round(tabHeight + tabInset + toolbarHeight + stackHeight -
                (containerHeight - getPaddingTop() - getPaddingBottom()));
    }

    /**
     * Calculates and returns the position of a tab, when it is swiped.
     *
     * @return The position, which has been calculated, in pixels as an {@link Float} value
     */
    private float calculateSwipePosition() {
        return arithmetics.getSize(Axis.ORTHOGONAL_AXIS, tabContainer);
    }

    /**
     * Calculates the position and state of a specific tab item, when located at the start.
     *
     * @param tabItem
     *         The tab item, whose position and state should be calculated, as an instance of the
     *         class {@link TabItem}. The tab item may not be null
     * @param predecessor
     *         The predecessor of the given tab item as an instance of the class {@link TabItem} or
     *         null, if the tab item does not have a predecessor
     */
    private void calculateAndClipStartPosition(@NonNull final TabItem tabItem,
                                               @Nullable final TabItem predecessor) {
        float position = calculateStartPosition(tabItem);
        dragHandler.clipTabPosition(position, tabItem, predecessor);
    }

    /**
     * Calculates and returns the position of a specific tab item, when located at the start.
     *
     * @param tabItem
     *         The tab item, whose position should be calculated, as an instance of the class {@link
     *         TabItem}. The tab item may not be null
     * @return The position, which has been calculated, as a {@link Float} value
     */
    private float calculateStartPosition(@NonNull final TabItem tabItem) {
        if (tabItem.getIndex() == 0) {
            return getCount() > stackedTabCount ? stackedTabCount * stackedTabSpacing :
                    (getCount() - 1) * stackedTabSpacing;

        } else {
            return -1;
        }
    }

    /**
     * Animates the bottom margin of a specific view.
     *
     * @param view
     *         The view, whose bottom margin should be animated, as an instance of the class {@link
     *         View}. The view may not be null
     * @param margin
     *         The bottom margin, which should be set by the animation, as an {@link Integer} value
     * @param animationDuration
     *         The duration of the animation in milliseconds as a {@link Long} value
     */
    private void animateBottomMargin(@NonNull final View view, final int margin,
                                     final long animationDuration) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
        final int initialMargin = layoutParams.bottomMargin;
        ValueAnimator animation = ValueAnimator.ofInt(margin - initialMargin);
        animation.setDuration(animationDuration);
        animation.addListener(new AnimationListenerWrapper(null));
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.setStartDelay(0);
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                FrameLayout.LayoutParams layoutParams =
                        (FrameLayout.LayoutParams) view.getLayoutParams();
                layoutParams.bottomMargin = initialMargin + (int) animation.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }

        });

        animation.start();
    }

    /**
     * Animates the visibility of the toolbar, which is shown, when the tab switcher is shown.
     *
     * @param visible
     *         True, if the toolbar should become visible, false otherwise
     * @param delay
     *         The delay of the animation in milliseconds as a {@link Long} value
     */
    private void animateToolbarVisibility(final boolean visible, final long delay) {
        if (toolbarAnimation != null) {
            toolbarAnimation.cancel();
        }

        float targetAlpha = visible ? 1 : 0;

        if (toolbar.getAlpha() != targetAlpha) {
            toolbarAnimation = toolbar.animate();
            toolbarAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
            toolbarAnimation.setDuration(toolbarVisibilityAnimationDuration);
            toolbarAnimation.setStartDelay(delay);
            toolbarAnimation.alpha(targetAlpha);
            toolbarAnimation.start();
        }
    }

    /**
     * Animates the position and size of a specific tab item in order to show the tab switcher.
     *
     * @param tabItem
     *         The tab item, which should be animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     */
    private void animateShowSwitcher(@NonNull final TabItem tabItem) {
        View view = tabItem.getView();
        arithmetics.setPivot(Axis.DRAGGING_AXIS, view,
                arithmetics.getDefaultPivot(Axis.DRAGGING_AXIS, view));
        arithmetics.setPivot(Axis.ORTHOGONAL_AXIS, view,
                arithmetics.getDefaultPivot(Axis.ORTHOGONAL_AXIS, view));
        float scale = arithmetics.getScale(view, true);

        if (tabItem.getIndex() < getSelectedTabIndex()) {
            arithmetics.setPosition(Axis.DRAGGING_AXIS, view,
                    arithmetics.getSize(Axis.DRAGGING_AXIS, tabContainer));
        } else if (tabItem.getIndex() > getSelectedTabIndex()) {
            FrameLayout.LayoutParams layoutParams =
                    (FrameLayout.LayoutParams) view.getLayoutParams();
            arithmetics.setPosition(Axis.DRAGGING_AXIS, view,
                    getLayout() == Layout.PHONE_LANDSCAPE ? 0 : layoutParams.topMargin);
        }

        if (tabViewBottomMargin == -1) {
            tabViewBottomMargin = calculateBottomMargin(view);
        }

        animateBottomMargin(view, calculateBottomMargin(view), showSwitcherAnimationDuration);
        ViewPropertyAnimator animation = view.animate();
        animation.setDuration(showSwitcherAnimationDuration);
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.setListener(
                new AnimationListenerWrapper(createShowSwitcherAnimationListener(tabItem)));
        arithmetics.animateScale(Axis.DRAGGING_AXIS, animation, scale);
        arithmetics.animateScale(Axis.ORTHOGONAL_AXIS, animation, scale);
        arithmetics.animatePosition(Axis.DRAGGING_AXIS, animation, view,
                tabItem.getTag().getPosition(), true);
        arithmetics.animatePosition(Axis.ORTHOGONAL_AXIS, animation, view, 0, true);
        animation.setStartDelay(0);
        animation.start();
        animateToolbarVisibility(isToolbarShown(), toolbarVisibilityAnimationDelay);
    }

    /**
     * Animates the position and size of a specific tab item in order to hide the tab switcher.
     *
     * @param tabItem
     *         The tab item, which should be animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     */
    private void animateHideSwitcher(@NonNull final TabItem tabItem) {
        View view = tabItem.getView();
        animateBottomMargin(view, -(tabInset + tabBorderWidth), hideSwitcherAnimationDuration);
        ViewPropertyAnimator animation = view.animate();
        animation.setDuration(hideSwitcherAnimationDuration);
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.setListener(
                new AnimationListenerWrapper(createHideSwitcherAnimationListener(tabItem)));
        arithmetics.animateScale(Axis.DRAGGING_AXIS, animation, 1);
        arithmetics.animateScale(Axis.ORTHOGONAL_AXIS, animation, 1);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
        arithmetics.animatePosition(Axis.ORTHOGONAL_AXIS, animation, view,
                getLayout() == Layout.PHONE_LANDSCAPE ? layoutParams.topMargin : 0, false);

        if (tabItem.getIndex() < getSelectedTabIndex()) {
            arithmetics.animatePosition(Axis.DRAGGING_AXIS, animation, view,
                    arithmetics.getSize(Axis.DRAGGING_AXIS, getTabSwitcher()), false);
        } else if (tabItem.getIndex() > getSelectedTabIndex()) {
            arithmetics.animatePosition(Axis.DRAGGING_AXIS, animation, view,
                    getLayout() == Layout.PHONE_LANDSCAPE ? 0 : layoutParams.topMargin, false);
        } else {
            arithmetics.animatePosition(Axis.DRAGGING_AXIS, animation, view,
                    getLayout() == Layout.PHONE_LANDSCAPE ? 0 : layoutParams.topMargin, false);
        }

        animation.setStartDelay(0);
        animation.start();
        animateToolbarVisibility(isToolbarShown() && isEmpty(), 0);
    }

    /**
     * Animates the position, size and alpha of a specific tab item in order to swipe it
     * orthogonally.
     *
     * @param tabItem
     *         The tab item, which should be animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     * @param remove
     *         True, if the tab should be removed after the animation has finished, false otherwise
     * @param velocity
     *         The velocity of the drag gesture, which triggered the animation, as a {@link Float}
     *         value or 0, if the animation was not triggered by a drag gesture
     * @param delay
     *         The delay after which the animation should be started in milliseconds as a {@link
     *         Long} value
     * @param animationType
     *         The animation type, which should be used, as a value of the enum {@link
     *         AnimationType} or null, if no specific animation type should be used
     * @param listener
     *         The listener, which should be notified about the progress of the animation, as an
     *         instance of the type {@link AnimatorListener} or null, if no listener should be
     *         notified
     */
    private void animateSwipe(@NonNull final TabItem tabItem, final boolean remove,
                              final float velocity, final long delay,
                              @Nullable AnimationType animationType,
                              @Nullable final AnimatorListener listener) {
        View view = tabItem.getView();
        float currentScale = arithmetics.getScale(view, true);
        float swipePosition = calculateSwipePosition();
        float currentPosition = arithmetics.getPosition(Axis.ORTHOGONAL_AXIS, view);
        AnimationType direction = animationType != null ? animationType :
                currentPosition < 0 ? AnimationType.SWIPE_LEFT : AnimationType.SWIPE_RIGHT;
        float targetPosition = remove ?
                (direction == AnimationType.SWIPE_LEFT ? -1 * swipePosition : swipePosition) : 0;
        float distance = Math.abs(targetPosition - currentPosition);
        long animationDuration;

        if (velocity > 0) {
            animationDuration = Math.round((distance / velocity) * 1000);
        } else {
            animationDuration = Math.round(swipeAnimationDuration * (distance / swipePosition));
        }

        ViewPropertyAnimator animation = view.animate();
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.setListener(new AnimationListenerWrapper(listener));
        animation.setDuration(animationDuration);
        arithmetics.animatePosition(Axis.ORTHOGONAL_AXIS, animation, view, targetPosition, true);
        arithmetics.animateScale(Axis.ORTHOGONAL_AXIS, animation,
                remove ? swipedTabScale * currentScale : currentScale);
        arithmetics.animateScale(Axis.DRAGGING_AXIS, animation,
                remove ? swipedTabScale * currentScale : currentScale);
        animation.alpha(remove ? swipedTabAlpha : 1);
        animation.setStartDelay(delay);
        animation.start();
    }

    /**
     * Animates the removal of a specific tab item.
     *
     * @param tabItem
     *         The tab item, which should be animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     * @param animationType
     *         The animation type, which should be used, as a value of the enum {@link
     *         AnimationType} or null, if no specific animation type should be used
     */
    private void animateRemove(@NonNull final TabItem tabItem,
                               @Nullable final AnimationType animationType) {
        View view = tabItem.getView();
        arithmetics.setPivot(Axis.DRAGGING_AXIS, view,
                arithmetics.getPivotWhenClosing(Axis.DRAGGING_AXIS, view));
        arithmetics.setPivot(Axis.ORTHOGONAL_AXIS, view,
                arithmetics.getPivotWhenClosing(Axis.ORTHOGONAL_AXIS, view));
        animateSwipe(tabItem, true, 0, 0, animationType, createRemoveAnimationListener(tabItem));
    }

    /**
     * Animates the position of a specific tab item in order to relocate it.
     *
     * @param tabItem
     *         The tab item, which should be animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     * @param position
     *         The position, the tab should be relocated to, in pixels as a {@link Float} value
     * @param tag
     *         The tag, which should be applied to the given tab item, as an instance of the class
     *         {@link Tag} or null, if no tag should be applied
     * @param delay
     *         The delay of the relocate animation in milliseconds as a {@link Long} value
     * @param listener
     *         The listener, which should be notified about the progress of the relocate animation,
     *         as an instance of the type {@link AnimatorListener} or null, if no listener should be
     *         notified
     */
    private void animateRelocate(@NonNull final TabItem tabItem, final float position,
                                 @Nullable final Tag tag, final long delay,
                                 @Nullable final AnimatorListener listener) {
        if (tag != null) {
            tabItem.getView().setTag(R.id.tag_properties, tag);
            tabItem.setTag(tag);
        }

        View view = tabItem.getView();
        ViewPropertyAnimator animation = view.animate();
        animation.setListener(new AnimationListenerWrapper(listener));
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.setDuration(relocateAnimationDuration);
        arithmetics.animatePosition(Axis.DRAGGING_AXIS, animation, view, position, true);
        animation.setStartDelay(delay);
        animation.start();
    }

    /**
     * Animates reverting an overshoot at the start.
     *
     * @param maxAngle
     *         The maximum angle, the tabs can be rotated by, in degrees as a {@link Float} value
     */
    private void animateRevertStartOvershoot(final float maxAngle) {
        boolean tilted = animateTilt(new AccelerateInterpolator(), maxAngle);

        if (tilted) {
            enqueuePendingAction(new Runnable() {

                @Override
                public void run() {
                    animateRevertStartOvershoot(new DecelerateInterpolator());
                }

            });
        } else {
            animateRevertStartOvershoot(new AccelerateDecelerateInterpolator());
        }
    }

    /**
     * Animates reverting an overshoot at the start using a specific interpolator.
     *
     * @param interpolator
     *         The interpolator, which should be used by the animation, as an instance of the type
     *         {@link Interpolator}. The interpolator may not be null
     */
    private void animateRevertStartOvershoot(@NonNull final Interpolator interpolator) {
        TabItem tabItem = TabItem.create(getTabSwitcher(), viewRecycler, 0);
        View view = tabItem.getView();
        arithmetics.setPivot(Axis.DRAGGING_AXIS, view,
                arithmetics.getDefaultPivot(Axis.DRAGGING_AXIS, view));
        arithmetics.setPivot(Axis.ORTHOGONAL_AXIS, view,
                arithmetics.getDefaultPivot(Axis.ORTHOGONAL_AXIS, view));
        float position = arithmetics.getPosition(Axis.DRAGGING_AXIS, view);
        float targetPosition = tabItem.getTag().getPosition();
        final float startPosition = arithmetics.getPosition(Axis.DRAGGING_AXIS, view);
        ValueAnimator animation = ValueAnimator.ofFloat(targetPosition - position);
        animation.setDuration(Math.round(revertOvershootAnimationDuration * Math.abs(
                (targetPosition - position) / (float) (stackedTabCount * stackedTabSpacing))));
        animation.addListener(new AnimationListenerWrapper(null));
        animation.setInterpolator(interpolator);
        animation.setStartDelay(0);
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(final ValueAnimator animation) {
                Iterator iterator = new Iterator.Builder(getTabSwitcher(), viewRecycler).create();
                TabItem tabItem;

                while ((tabItem = iterator.next()) != null) {
                    if (tabItem.getIndex() == 0) {
                        View view = tabItem.getView();
                        arithmetics.setPosition(Axis.DRAGGING_AXIS, view,
                                startPosition + (float) animation.getAnimatedValue());
                    } else if (tabItem.isInflated()) {
                        View firstView = iterator.first().getView();
                        View view = tabItem.getView();
                        view.setVisibility(arithmetics.getPosition(Axis.DRAGGING_AXIS, firstView) <=
                                arithmetics.getPosition(Axis.DRAGGING_AXIS, view) ? View.INVISIBLE :
                                View.VISIBLE);
                    }
                }
            }

        });

        animation.start();
    }

    /**
     * Animates reverting an overshoot at the end.
     *
     * @param maxAngle
     *         The maximum angle, the tabs can be rotated by, in degrees as a {@link Float} value
     */
    private void animateRevertEndOvershoot(final float maxAngle) {
        animateTilt(new AccelerateDecelerateInterpolator(), maxAngle);
    }

    /**
     * Animates to rotation of all tabs to be reset to normal.
     *
     * @param interpolator
     *         The interpolator, which should be used by the animation, as an instance of the type
     *         {@link Interpolator}. The interpolator may not be null
     * @param maxAngle
     *         The angle, the tabs may be rotated by at maximum, in degrees as a {@link Float}
     *         value
     * @return True, if at least one tab was animated, false otherwise
     */
    private boolean animateTilt(@NonNull final Interpolator interpolator, final float maxAngle) {
        Iterator iterator =
                new Iterator.Builder(getTabSwitcher(), viewRecycler).reverse(true).create();
        TabItem tabItem;
        boolean result = false;

        while ((tabItem = iterator.next()) != null) {
            if (tabItem.isInflated()) {
                View view = tabItem.getView();

                if (arithmetics.getRotation(Axis.ORTHOGONAL_AXIS, view) != 0) {
                    result = true;
                    ViewPropertyAnimator animation = view.animate();
                    animation.setListener(new AnimationListenerWrapper(
                            createRevertOvershootAnimationListener(view)));
                    animation.setDuration(Math.round(revertOvershootAnimationDuration *
                            (Math.abs(arithmetics.getRotation(Axis.ORTHOGONAL_AXIS, view)) /
                                    maxAngle)));
                    animation.setInterpolator(interpolator);
                    arithmetics.animateRotation(Axis.ORTHOGONAL_AXIS, animation, 0);
                    animation.setStartDelay(0);
                    animation.start();
                }
            }
        }

        return result;
    }

    /**
     * Animates flinging the tabs.
     *
     * @param distance
     *         The distance, the tabs should be flinged, in pixels as a {@link Float} value
     * @param duration
     *         The duration of the fling in milliseconds as a {@link Long} value
     */
    private void animateFling(final float distance, final long duration) {
        flingAnimation = new FlingAnimation(distance);
        flingAnimation.setFillAfter(true);
        flingAnimation.setAnimationListener(createFlingAnimationListener());
        flingAnimation.setDuration(duration);
        flingAnimation.setInterpolator(new DecelerateInterpolator());
        getTabSwitcher().startAnimation(flingAnimation);
    }

    /**
     * Creates and returns a layout listener, which allows to animate the position and size of a tab
     * in order to show the tab switcher, once its view has been inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be animated, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createShowSwitcherLayoutListener(
            @NonNull final TabItem tabItem) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                animateShowSwitcher(tabItem);
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to animate the position and size of a tab
     * in order to hide the tab switcher, once its view has been inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be animated, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createHideSwitcherLayoutListener(
            @NonNull final TabItem tabItem) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                animateHideSwitcher(tabItem);
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to remove a tab, once its view has been
     * inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which should be removed, as an instance
     *         of the class {@link TabItem}. The tab item may not be null
     * @param animationType
     *         The animation type, which should be used, as a value of the enum {@link
     *         AnimationType} or null, if no specific animation type should be used
     * @return The listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createRemoveLayoutListener(@NonNull final TabItem tabItem,
                                                              @Nullable final AnimationType animationType) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                animateRemove(tabItem, animationType);
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to relocate a tab, once its view has been
     * inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which should be relocated, as an instance
     *         of the class {@link TabItem}. The tab item may not be null
     * @param position
     *         The position, the tab should be relocated to, in pixels as a {@link Float} value
     * @param tag
     *         The tag, which should be applied to the given tab item, as an instance of the class
     *         {@link Tag} or null, if no tag should be applied
     * @param delay
     *         The delay of the relocate animation in milliseconds as a {@link Long} value
     * @param listener
     *         The listener, which should be notified about the progress of the relocate animation,
     *         as an instance of the type {@link AnimatorListener} or null, if no listener should be
     *         notified
     * @return The listener, which has been created, as an instance of the class {@link
     * OnGlobalLayoutListener}. The listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createRelocateLayoutListener(@NonNull final TabItem tabItem,
                                                                final float position,
                                                                @Nullable final Tag tag,
                                                                final long delay,
                                                                @Nullable final AnimatorListener listener) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                animateRelocate(tabItem, position, tag, delay, listener);
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to adapt the size and position of a tab,
     * once its view has been inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be adapted, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     * @param layoutListener
     *         The layout lister, which should be notified, when the created listener is invoked, as
     *         an instance of the type {@link OnGlobalLayoutListener} or null, if no listener should
     *         be notified
     * @return The layout listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The layout listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createInflateViewLayoutListener(@NonNull final TabItem tabItem,
                                                                   @Nullable final OnGlobalLayoutListener layoutListener) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                View view = tabItem.getView();
                ViewUtil.removeOnGlobalLayoutListener(view.getViewTreeObserver(), this);
                adaptViewSize(tabItem);
                updateView(tabItem);

                if (layoutListener != null) {
                    layoutListener.onGlobalLayout();
                }
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to update the view, which is used to
     * visualize a tab, when an animation, which is used to show the tab switcher, has been
     * finished.
     *
     * @param tabItem
     *         The tab item, which has been animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     * @return The animation listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createShowSwitcherAnimationListener(@NonNull final TabItem tabItem) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                updateView(tabItem);
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to inflate or remove the view, which
     * is used to visualize a tab, when an animation, which is used to hide the tab switcher, has
     * been finished.
     *
     * @param tabItem
     *         The tab item, which has been animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     * @return The animation listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createHideSwitcherAnimationListener(@NonNull final TabItem tabItem) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);

                if (tabItem.getIndex() == getSelectedTabIndex()) {
                    viewRecycler.inflate(tabItem);
                } else {
                    viewRecycler.remove(tabItem);
                    viewRecycler.clearCache();
                }
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to remove all tabs, when the
     * animation, which is used to swipe all tabs, has been finished.
     *
     * @return The animation listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createClearAnimationListener() {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);

                for (Tab tab : clearTabsInternal()) {
                    tab.removeCallback(recyclerAdapter);
                }

                viewRecycler.removeAll();
                setSelectedTabIndex(-1);
                animateToolbarVisibility(isToolbarShown(), 0);
            }

        };
    }

    /**
     * Creates and returns a listener, which allows to handle, when a tab has been swiped, but was
     * not removed.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which has been swiped, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createSwipeAnimationListener(@NonNull final TabItem tabItem) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                View view = tabItem.getView();
                adaptStackOnSwipeAborted(tabItem, tabItem.getIndex() + 1);
                tabItem.getTag().setClosing(false);
                arithmetics.setPivot(Axis.DRAGGING_AXIS, view,
                        arithmetics.getDefaultPivot(Axis.DRAGGING_AXIS, view));
                animateToolbarVisibility(true, 0);
            }

        };
    }

    /**
     * Creates and returns a listener, which allows to relocate all previous tabs, when a tab has
     * been removed.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which has been removed, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createRemoveAnimationListener(@NonNull final TabItem tabItem) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(final Animator animation) {
                super.onAnimationStart(animation);

                if (tabItem.getTag().getState() == State.STACKED_END) {
                    relocateWhenRemovingStackedTab(tabItem, false);
                } else if (tabItem.getTag().getState() == State.STACKED_START) {
                    relocateWhenRemovingStackedTab(tabItem, true);
                } else {
                    relocateWhenRemovingFloatingTab(tabItem);
                }
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);

                int index = tabItem.getIndex();
                viewRecycler.remove(tabItem);
                Tab tab = removeTabInternal(index);
                tab.removeCallback(recyclerAdapter);

                if (isEmpty()) {
                    setSelectedTabIndex(-1);
                    animateToolbarVisibility(isToolbarShown(), 0);
                } else if (getSelectedTabIndex() == tabItem.getIndex()) {
                    if (getSelectedTabIndex() > 0) {
                        setSelectedTabIndex(getSelectedTabIndex() - 1);
                    } else {
                        setSelectedTabIndex(getSelectedTabIndex());
                    }
                }
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to update the top stack, when an
     * animation, which is used to relocate a tab, has been started.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which is relocated, as an instance of the
     *         class {@link TabItem}. The tab item may not be null
     * @param listener
     *         The listener, which should be notified, when the created listener is invoked, as an
     *         instance of the type {@link AnimatorListener} or null, if no listener should be
     *         notified
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createRelocateAnimationListenerWrapper(@NonNull final TabItem tabItem,
                                                                    @Nullable final AnimatorListener listener) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(final Animator animation) {
                super.onAnimationStart(animation);

                if (listener != null) {
                    listener.onAnimationStart(animation);
                }
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                adaptStackOnSwipeAborted(tabItem, tabItem.getIndex());

                if (listener != null) {
                    listener.onAnimationEnd(animation);
                }
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to update or remove the view, which
     * is used to visualize a tab, when the animation, which has been used to relocate it, has been
     * ended.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which has been relocated, as an instance
     *         of the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createRelocateAnimationListener(@NonNull final TabItem tabItem) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(final Animator animation) {
                super.onAnimationStart(animation);
                tabItem.getView().setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);

                if (tabItem.isVisible()) {
                    updateView(tabItem);
                } else {
                    viewRecycler.remove(tabItem);
                }
            }

        };
    }

    /**
     * Creates and returns an animation, listener, which allows to adapt the pivot of a specific
     * view, when an animation, which reverted an overshoot, has been ended.
     *
     * @param view
     *         The view, whose pivot should be adapted, as an instance of the class {@link View}.
     *         The view may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createRevertOvershootAnimationListener(@NonNull final View view) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                arithmetics.setPivot(Axis.DRAGGING_AXIS, view,
                        arithmetics.getDefaultPivot(Axis.DRAGGING_AXIS, view));
                arithmetics.setPivot(Axis.ORTHOGONAL_AXIS, view,
                        arithmetics.getDefaultPivot(Axis.DRAGGING_AXIS, view));
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to handle, when a fling animation
     * ended.
     *
     * @return The listener, which has been created, as an instance of the class {@link
     * AnimationListener}. The listener may not be null
     */
    @NonNull
    private AnimationListener createFlingAnimationListener() {
        return new AnimationListener() {

            @Override
            public void onAnimationStart(final Animation animation) {

            }

            @Override
            public void onAnimationEnd(final Animation animation) {
                dragHandler.handleRelease(null);
                flingAnimation = null;
                executePendingAction();
            }

            @Override
            public void onAnimationRepeat(final Animation animation) {

            }

        };
    }

    /**
     * Adapts the stack, which is located at the start, when swiping a tab.
     *
     * @param swipedTabItem
     *         The tab item, which corresponds to the swiped tab, as an instance of the class {@link
     *         TabItem}. The tab item may not be null
     * @param successorIndex
     *         The index of the tab, which is located after the swiped tab, as an {@link Integer}
     *         value
     */
    private void adaptStackOnSwipe(@NonNull final TabItem swipedTabItem, final int successorIndex) {
        if (swipedTabItem.getTag().getState() == State.STACKED_START_ATOP) {
            TabItem tabItem = TabItem.create(getTabSwitcher(), viewRecycler, successorIndex);

            if (tabItem.getTag().getState() == State.HIDDEN) {
                Pair<Float, State> pair = dragHandler
                        .calculatePositionAndStateWhenStackedAtStart(swipedTabItem, null);
                tabItem.getTag().setPosition(pair.first);
                tabItem.getTag().setState(pair.second);
                inflateView(tabItem, null);
            }
        }
    }

    /**
     * Adapts the stack, which located at the start, when swiping a tab has been aborted.
     *
     * @param swipedTabItem
     *         The tab item, which corresponds to the swiped tab, as an instance of the class {@link
     *         TabItem}. The tab item may not be null
     * @param successorIndex
     *         The index of the the tab, which is located after the swiped tab, as an {@link
     *         Integer} value
     */
    private void adaptStackOnSwipeAborted(@NonNull final TabItem swipedTabItem,
                                          final int successorIndex) {
        if (swipedTabItem.getTag().getState() == State.STACKED_START_ATOP) {
            TabItem tabItem = TabItem.create(getTabSwitcher(), viewRecycler, successorIndex);
            tabItem.getTag().setPosition(Float.NaN);
            tabItem.getTag().setState(State.HIDDEN);
            viewRecycler.remove(tabItem);
        }
    }

    /**
     * Inflates or removes the view, which is used to visualize a specific tab, depending on the
     * tab's current state.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be inflated or removed,
     *         as an instance of the type {@link TabItem}. The tab item may not be null
     */
    private void inflateOrRemoveView(@NonNull final TabItem tabItem) {
        if (tabItem.isInflated() && !tabItem.isVisible()) {
            viewRecycler.remove(tabItem);
        } else if (tabItem.isVisible()) {
            if (!tabItem.isInflated()) {
                inflateView(tabItem, null);
            } else {
                updateView(tabItem);
            }
        }
    }

    /**
     * Inflates the view, which is used to visualize a specific tab.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be inflated, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     * @param listener
     *         The layout listener, which should be notified, when the view has been inflated, as an
     *         instance of the type {@link OnGlobalLayoutListener} or null, if no listener should be
     *         notified
     */
    private void inflateView(@NonNull final TabItem tabItem,
                             @Nullable final OnGlobalLayoutListener listener) {
        boolean inflated = viewRecycler.inflate(tabItem, tabViewBottomMargin);

        if (inflated) {
            View view = tabItem.getView();
            view.getViewTreeObserver().addOnGlobalLayoutListener(new LayoutListenerWrapper(view,
                    createInflateViewLayoutListener(tabItem, listener)));
        } else {
            adaptViewSize(tabItem);
            updateView(tabItem);

            if (listener != null) {
                listener.onGlobalLayout();
            }
        }
    }

    /**
     * Adapts the size of the view, which is used to visualize a specific tab.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be adapted, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     */
    private void adaptViewSize(@NonNull final TabItem tabItem) {
        View view = tabItem.getView();
        arithmetics.setPivot(Axis.DRAGGING_AXIS, view,
                arithmetics.getDefaultPivot(Axis.DRAGGING_AXIS, view));
        arithmetics.setPivot(Axis.ORTHOGONAL_AXIS, view,
                arithmetics.getDefaultPivot(Axis.ORTHOGONAL_AXIS, view));
        float scale = arithmetics.getScale(view, true);
        arithmetics.setScale(Axis.DRAGGING_AXIS, view, scale);
        arithmetics.setScale(Axis.ORTHOGONAL_AXIS, view, scale);
    }

    /**
     * Updates the view, which is used to visualize a specific tab.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be updated, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     */
    private void updateView(@NonNull final TabItem tabItem) {
        float position = tabItem.getTag().getPosition();
        View view = tabItem.getView();
        view.setAlpha(1f);
        arithmetics.setPivot(Axis.DRAGGING_AXIS, view,
                arithmetics.getDefaultPivot(Axis.DRAGGING_AXIS, view));
        arithmetics.setPivot(Axis.ORTHOGONAL_AXIS, view,
                arithmetics.getDefaultPivot(Axis.ORTHOGONAL_AXIS, view));
        arithmetics.setPosition(Axis.DRAGGING_AXIS, view, position);
        arithmetics.setPosition(Axis.ORTHOGONAL_AXIS, view, 0);
        arithmetics.setRotation(Axis.ORTHOGONAL_AXIS, view, 0);
    }

    /**
     * Relocates all previous tabs, when a floating tab has been removed.
     *
     * @param removedTabItem
     *         The tab item, which corresponds to the tab, which has been removed, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     */
    private void relocateWhenRemovingFloatingTab(@NonNull final TabItem removedTabItem) {
        if (removedTabItem.getIndex() > 0) {
            Iterator iterator = new Iterator.Builder(getTabSwitcher(), viewRecycler).reverse(true)
                    .start(removedTabItem.getIndex()).create();
            TabItem tabItem;
            Tag previousTag = null;
            boolean abort = false;

            while ((tabItem = iterator.next()) != null && !abort) {
                Tag currentTag = tabItem.getTag().clone();

                if (previousTag != null) {
                    if (tabItem.getTag().getState() != State.FLOATING) {
                        abort = true;
                    }

                    float relocatePosition = previousTag.getPosition();
                    long startDelay = (removedTabItem.getIndex() - tabItem.getIndex()) *
                            relocateAnimationDelay;
                    AnimatorListener relocateAnimationListener =
                            createRelocateAnimationListener(tabItem);
                    AnimatorListener listener =
                            tabItem.getIndex() == removedTabItem.getIndex() - 1 ?
                                    createRelocateAnimationListenerWrapper(removedTabItem,
                                            relocateAnimationListener) : relocateAnimationListener;

                    if (tabItem.isInflated()) {
                        animateRelocate(tabItem, relocatePosition, previousTag, startDelay,
                                listener);
                    } else {
                        Pair<Float, State> pair =
                                dragHandler.calculatePositionAndStateWhenStackedAtEnd(tabItem);
                        tabItem.getTag().setPosition(pair.first);
                        tabItem.getTag().setState(pair.second);
                        inflateView(tabItem,
                                createRelocateLayoutListener(tabItem, relocatePosition, previousTag,
                                        startDelay, listener));
                        tabItem.getView().setVisibility(View.INVISIBLE);
                    }
                }

                previousTag = currentTag;
                previousTag.setClosing(false);
            }
        }
    }

    /**
     * Relocates all neighboring tabs, when a stacked tab has been removed.
     *
     * @param removedTabItem
     *         The tab item, which corresponds to the tab, which has been removed, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @param start
     *         True, if the removed tab was part of the stack, which is located at the start, false,
     *         if it was part of the stack, which is located at the end
     */
    private void relocateWhenRemovingStackedTab(@NonNull final TabItem removedTabItem,
                                                final boolean start) {
        int startIndex = removedTabItem.getIndex() + (start ? -1 : 1);
        Iterator iterator = new Iterator.Builder(getTabSwitcher(), viewRecycler).reverse(start)
                .start(removedTabItem.getIndex()).create();
        TabItem tabItem;
        Float previousProjectedPosition = null;

        while ((tabItem = iterator.next()) != null &&
                (tabItem.getTag().getState() == State.HIDDEN ||
                        tabItem.getTag().getState() == State.STACKED_START ||
                        tabItem.getTag().getState() == State.STACKED_END)) {
            float projectedPosition = tabItem.getTag().getPosition();

            if (previousProjectedPosition != null) {
                if (tabItem.getTag().getState() == State.HIDDEN) {
                    TabItem previous = iterator.previous();
                    tabItem.getTag().setState(previous.getTag().getState());

                    if (start) {
                        tabItem.getTag().setPosition(previousProjectedPosition);
                    }

                    if (tabItem.isVisible()) {
                        Pair<Float, State> pair = start ? dragHandler
                                .calculatePositionAndStateWhenStackedAtStart(previous, tabItem) :
                                dragHandler.calculatePositionAndStateWhenStackedAtEnd(previous);
                        tabItem.getTag().setPosition(pair.first);
                        tabItem.getTag().setState(pair.second);
                        inflateView(tabItem, null);
                    }

                    break;
                } else {
                    tabItem.getTag().setPosition(previousProjectedPosition);
                    long startDelay = (start ? (startIndex + 1 - tabItem.getIndex()) :
                            (tabItem.getIndex() - startIndex)) * relocateAnimationDelay;
                    animateRelocate(tabItem, previousProjectedPosition, null, startDelay,
                            createRelocateAnimationListener(tabItem));
                }
            }

            previousProjectedPosition = projectedPosition;
        }
    }

    /**
     * Swipes a specific tab.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which should be swiped, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @param distance
     *         The distance, the tab should be swiped by, in pixels as a {@link Float} value
     */
    private void swipe(@NonNull final TabItem tabItem, final float distance) {
        View view = tabItem.getView();

        if (!tabItem.getTag().isClosing()) {
            adaptStackOnSwipe(tabItem, tabItem.getIndex() + 1);
        }

        tabItem.getTag().setClosing(true);
        arithmetics.setPivot(Axis.DRAGGING_AXIS, view,
                arithmetics.getPivotWhenClosing(Axis.DRAGGING_AXIS, view));
        arithmetics.setPivot(Axis.ORTHOGONAL_AXIS, view,
                arithmetics.getPivotWhenClosing(Axis.ORTHOGONAL_AXIS, view));
        float scale = arithmetics.getScale(view, true);
        arithmetics.setPosition(Axis.ORTHOGONAL_AXIS, view, distance);
        float ratio = 1 - (Math.abs(distance) / calculateSwipePosition());
        float scaledClosedTabScale = swipedTabScale * scale;
        float targetScale = scaledClosedTabScale + ratio * (scale - scaledClosedTabScale);
        arithmetics.setScale(Axis.DRAGGING_AXIS, view, targetScale);
        arithmetics.setScale(Axis.ORTHOGONAL_AXIS, view, targetScale);
        view.setAlpha(swipedTabAlpha + ratio * (1 - swipedTabAlpha));
    }

    /**
     * Moves the first tab to overlap the other tabs, when overshooting at the start.
     *
     * @param position
     *         The position of the first tab in pixels as a {@link Float} value
     */
    private void startOvershoot(final float position) {
        Iterator iterator = new Iterator.Builder(getTabSwitcher(), viewRecycler).create();
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null) {
            if (tabItem.getIndex() == 0) {
                View view = tabItem.getView();
                arithmetics.setPivot(Axis.DRAGGING_AXIS, view,
                        arithmetics.getDefaultPivot(Axis.DRAGGING_AXIS, view));
                arithmetics.setPivot(Axis.ORTHOGONAL_AXIS, view,
                        arithmetics.getDefaultPivot(Axis.ORTHOGONAL_AXIS, view));
                arithmetics.setPosition(Axis.DRAGGING_AXIS, view, position);
            } else if (tabItem.isInflated()) {
                View firstView = iterator.first().getView();
                View view = tabItem.getView();
                view.setVisibility(arithmetics.getPosition(Axis.DRAGGING_AXIS, firstView) <=
                        arithmetics.getPosition(Axis.DRAGGING_AXIS, view) ? View.INVISIBLE :
                        View.VISIBLE);
            }
        }
    }

    /**
     * Tilts the tabs, when overshooting at the start.
     *
     * @param angle
     *         The angle, the tabs should be rotated by, in degrees as a {@link Float} value
     */
    private void tiltOnStartOvershoot(final float angle) {
        Iterator iterator = new Iterator.Builder(getTabSwitcher(), viewRecycler).create();
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null) {
            View view = tabItem.getView();

            if (tabItem.getIndex() == 0) {
                view.setCameraDistance(getMaxCameraDistance());
                arithmetics.setPivot(Axis.DRAGGING_AXIS, view,
                        arithmetics.getPivotOnOvershootStart(Axis.DRAGGING_AXIS, view));
                arithmetics.setPivot(Axis.ORTHOGONAL_AXIS, view,
                        arithmetics.getPivotOnOvershootStart(Axis.ORTHOGONAL_AXIS, view));
                arithmetics.setRotation(Axis.ORTHOGONAL_AXIS, view, angle);
            } else if (tabItem.isInflated()) {
                tabItem.getView().setVisibility(View.INVISIBLE);
            }
        }
    }

    /**
     * Tilts the tabs, when overshooting at the end.
     *
     * @param angle
     *         The angle, the tabs should be rotated by, in degrees as a {@link Float} value
     */
    private void tiltOnEndOvershoot(final float angle) {
        float maxCameraDistance = getMaxCameraDistance();
        float minCameraDistance = maxCameraDistance / 2f;
        int firstVisibleIndex = -1;
        Iterator iterator = new Iterator.Builder(getTabSwitcher(), viewRecycler).create();
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null) {
            if (tabItem.isInflated()) {
                View view = tabItem.getView();

                if (!iterator.hasNext()) {
                    view.setCameraDistance(maxCameraDistance);
                } else if (firstVisibleIndex == -1) {
                    view.setCameraDistance(minCameraDistance);

                    if (tabItem.getTag().getState() == State.FLOATING) {
                        firstVisibleIndex = tabItem.getIndex();
                    }
                } else {
                    int diff = tabItem.getIndex() - firstVisibleIndex;
                    float ratio = (float) diff / (float) (getCount() - firstVisibleIndex);
                    view.setCameraDistance(
                            minCameraDistance + (maxCameraDistance - minCameraDistance) * ratio);
                }

                arithmetics.setPivot(Axis.DRAGGING_AXIS, view,
                        arithmetics.getPivotOnOvershootEnd(Axis.DRAGGING_AXIS, view));
                arithmetics.setPivot(Axis.ORTHOGONAL_AXIS, view,
                        arithmetics.getPivotOnOvershootEnd(Axis.ORTHOGONAL_AXIS, view));
                arithmetics.setRotation(Axis.ORTHOGONAL_AXIS, view, angle);
            }
        }
    }

    /**
     * Returns the maximum camera distance.
     *
     * @return The maximum camera distance in pixels as a {@link Float} value
     */
    private float getMaxCameraDistance() {
        float density = getContext().getResources().getDisplayMetrics().density;
        return density * 1280;
    }

    /**
     * Obtains the view's background from a specific typed array.
     *
     * @param typedArray
     *         The typed array, the background should be obtained from, as an instance of the class
     *         {@link TypedArray}. The typed array may not be null
     */
    private void obtainBackground(@NonNull final TypedArray typedArray) {
        int resourceId = typedArray.getResourceId(R.styleable.TabSwitcher_android_background, 0);

        if (resourceId != 0) {
            ViewUtil.setBackground(getTabSwitcher(),
                    ContextCompat.getDrawable(getContext(), resourceId));
        } else {
            int defaultValue =
                    ContextCompat.getColor(getContext(), R.color.tab_switcher_background_color);
            int color =
                    typedArray.getColor(R.styleable.TabSwitcher_android_background, defaultValue);
            getTabSwitcher().setBackgroundColor(color);
        }
    }

    /**
     * Obtains the background color of tabs from a specific typed array.
     *
     * @param typedArray
     *         The typed array, the background color should be obtained from, as an instance of the
     *         class {@link TypedArray}. The typed array may not be null
     */
    private void obtainTabBackgroundColor(@NonNull final TypedArray typedArray) {
        int defaultValue = ContextCompat.getColor(getContext(), R.color.tab_background_color);
        setTabBackgroundColor(
                typedArray.getColor(R.styleable.TabSwitcher_tabBackgroundColor, defaultValue));
    }

    /**
     * Creates a new layout, which implements the functionality of a {@link TabSwitcher} on
     * smartphones.
     *
     * @param tabSwitcher
     *         The tab switcher, the layout belongs to, as an instance of the class {@link
     *         TabSwitcher}. The tab switcher may not be null
     */
    public PhoneTabSwitcherLayout(@NonNull final TabSwitcher tabSwitcher) {
        super(tabSwitcher);
        arithmetics = new Arithmetics(tabSwitcher);
        Resources resources = tabSwitcher.getResources();
        tabInset = resources.getDimensionPixelSize(R.dimen.tab_inset);
        tabBorderWidth = resources.getDimensionPixelSize(R.dimen.tab_border_width);
        stackedTabCount = resources.getInteger(R.integer.stacked_tab_count);
        stackedTabSpacing = resources.getDimensionPixelSize(R.dimen.stacked_tab_spacing);
        maxTabSpacing = resources.getDimensionPixelSize(R.dimen.max_tab_spacing);
        TypedValue typedValue = new TypedValue();
        resources.getValue(R.dimen.swiped_tab_scale, typedValue, true);
        swipedTabScale = typedValue.getFloat();
        resources.getValue(R.dimen.swiped_tab_alpha, typedValue, true);
        swipedTabAlpha = typedValue.getFloat();
        showSwitcherAnimationDuration =
                resources.getInteger(R.integer.show_switcher_animation_duration);
        hideSwitcherAnimationDuration =
                resources.getInteger(R.integer.hide_switcher_animation_duration);
        toolbarVisibilityAnimationDuration =
                resources.getInteger(R.integer.toolbar_visibility_animation_duration);
        toolbarVisibilityAnimationDelay =
                resources.getInteger(R.integer.toolbar_visibility_animation_delay);
        swipeAnimationDuration = resources.getInteger(R.integer.swipe_animation_duration);
        clearAnimationDelay = resources.getInteger(R.integer.clear_animation_delay);
        relocateAnimationDuration = resources.getInteger(R.integer.relocate_animation_duration);
        relocateAnimationDelay = resources.getInteger(R.integer.relocate_animation_delay);
        revertOvershootAnimationDuration =
                resources.getInteger(R.integer.revert_overshoot_animation_duration);
        tabViewBottomMargin = -1;
        toolbarAnimation = null;
        flingAnimation = null;
    }

    @Override
    protected final void onDecoratorChanged(@NonNull final TabSwitcherDecorator decorator) {
        childViewRecycler.setDecorator(decorator);
        recyclerAdapter.clearCachedPreviews();
    }

    @Override
    protected final void onPaddingChanged(final int left, final int top, final int right,
                                          final int bottom) {
        FrameLayout.LayoutParams toolbarLayoutParams =
                (FrameLayout.LayoutParams) toolbar.getLayoutParams();
        toolbarLayoutParams.setMargins(left, top, right, 0);
        Iterator iterator = new Iterator.Builder(getTabSwitcher(), viewRecycler).create();
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null) {
            TabViewHolder viewHolder = tabItem.getViewHolder();

            if (viewHolder != null) {
                recyclerAdapter.adaptPadding(viewHolder);
            }
        }
    }

    @Override
    protected final void onTabBackgroundColorChanged(@ColorInt final int color) {
        for (Tab tab : this) {
            recyclerAdapter.onColorChanged(tab);
        }
    }

    @Override
    public final void obtainStyledAttributes(@NonNull final TypedArray typedArray) {
        super.obtainStyledAttributes(typedArray);
        obtainBackground(typedArray);
        obtainTabBackgroundColor(typedArray);
    }

    @Override
    public final void inflateLayout() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        toolbar =
                (Toolbar) inflater.inflate(R.layout.tab_switcher_toolbar, getTabSwitcher(), false);
        getTabSwitcher().addView(toolbar);
        tabContainer = new FrameLayout(getContext());
        getTabSwitcher().addView(tabContainer, FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        childViewRecycler = new ChildViewRecycler(inflater);
        recyclerAdapter = new RecyclerAdapter(getTabSwitcher(), childViewRecycler);
        viewRecycler = new ViewRecycler<>(tabContainer, recyclerAdapter, inflater,
                Collections.reverseOrder(TabItem.COMPARATOR));
        recyclerAdapter.setViewRecycler(viewRecycler);
        int dragThreshold =
                getTabSwitcher().getResources().getDimensionPixelSize(R.dimen.drag_threshold);
        dragHandler = new DragHandler(getTabSwitcher(), viewRecycler, arithmetics, dragThreshold);
        dragHandler.setCallback(this);
    }

    @Override
    public final boolean handleTouchEvent(@NonNull final MotionEvent event) {
        return dragHandler.handleTouchEvent(event);
    }

    @Override
    public final void addCloseTabListener(@NonNull final TabCloseListener listener) {
        recyclerAdapter.addCloseTabListener(listener);
    }

    @Override
    public final void removeCloseTabListener(@NonNull final TabCloseListener listener) {
        recyclerAdapter.removeCloseTabListener(listener);
    }

    @NonNull
    @Override
    public final Layout getLayout() {
        return getOrientation(getContext()) == Orientation.LANDSCAPE ? Layout.PHONE_LANDSCAPE :
                Layout.PHONE_PORTRAIT;
    }

    @Override
    public final boolean isAnimationRunning() {
        return super.isAnimationRunning() || flingAnimation != null;
    }

    @Override
    public final void addTab(@NonNull final Tab tab, final int index,
                             @NonNull final AnimationType animationType) {
        ensureNotNull(tab, "The tab may not be null");
        ensureNotNull(animationType, "The animation type may not be null");
        enqueuePendingAction(new Runnable() {

            @Override
            public void run() {
                tab.addCallback(recyclerAdapter);
                addTabInternal(index, tab);

                if (getCount() == 1) {
                    setSelectedTabIndex(0);
                }

                if (!isSwitcherShown()) {
                    toolbar.setAlpha(0);

                    if (getSelectedTabIndex() == index && ViewCompat.isLaidOut(getTabSwitcher())) {
                        viewRecycler.inflate(TabItem.create(getTabSwitcher(), viewRecycler, index));
                    }
                } else {
                    // TODO: Add support for adding tab, while switcher is shown
                }
            }

        });
    }

    @Override
    public final void removeTab(@NonNull final Tab tab,
                                @NonNull final AnimationType animationType) {
        ensureNotNull(tab, "The tab may not be null");
        enqueuePendingAction(new Runnable() {

            @Override
            public void run() {
                int index = indexOfOrThrowException(tab);
                TabItem tabItem = TabItem.create(getTabSwitcher(), viewRecycler, index);

                if (!isSwitcherShown()) {
                    viewRecycler.remove(tabItem);
                    Tab tab = removeTabInternal(index);
                    tab.removeCallback(recyclerAdapter);

                    if (isEmpty()) {
                        setSelectedTabIndex(-1);
                        toolbar.setAlpha(isToolbarShown() ? 1 : 0);
                    } else if (getSelectedTabIndex() == index) {
                        if (getSelectedTabIndex() > 0) {
                            setSelectedTabIndex(getSelectedTabIndex() - 1);
                        } else {
                            setSelectedTabIndex(getSelectedTabIndex());
                        }

                        viewRecycler.inflate(TabItem.create(getTabSwitcher(), viewRecycler,
                                getSelectedTabIndex()));
                    }
                } else {
                    adaptStackOnSwipe(tabItem, tabItem.getIndex() + 1);
                    tabItem.getTag().setClosing(true);

                    if (tabItem.isInflated()) {
                        animateRemove(tabItem, animationType);
                    } else {
                        inflateView(tabItem, createRemoveLayoutListener(tabItem, animationType));
                    }
                }
            }

        });
    }

    @Override
    public final void clear(@NonNull final AnimationType animationType) {
        enqueuePendingAction(new Runnable() {

            @Override
            public void run() {
                if (!isSwitcherShown()) {
                    for (Tab tab : clearTabsInternal()) {
                        tab.removeCallback(recyclerAdapter);
                    }

                    viewRecycler.removeAll();
                    setSelectedTabIndex(-1);
                    toolbar.setAlpha(isToolbarShown() ? 1 : 0);
                } else {
                    Iterator iterator = new Iterator.Builder(getTabSwitcher(), viewRecycler).
                            reverse(true).create();
                    TabItem tabItem;
                    int startDelay = 0;

                    while ((tabItem = iterator.next()) != null) {
                        TabItem previous = iterator.previous();

                        if (tabItem.getTag().getState() == State.FLOATING || (previous != null &&
                                previous.getTag().getState() == State.FLOATING)) {
                            startDelay += clearAnimationDelay;
                        }

                        if (tabItem.isInflated()) {
                            animateSwipe(tabItem, true, 0, startDelay, animationType,
                                    !iterator.hasNext() ? createClearAnimationListener() : null);
                        }
                    }
                }
            }

        });
    }

    // TODO: Calling this method should also work when the view is not yet inflated
    @Override
    public final void showSwitcher() {
        enqueuePendingAction(new Runnable() {

            @Override
            public void run() {
                if (!isSwitcherShown()) {
                    setSwitcherShown(true);
                    dragHandler.reset();
                    Iterator iterator =
                            new Iterator.Builder(getTabSwitcher(), viewRecycler).create();
                    TabItem tabItem;

                    while ((tabItem = iterator.next()) != null) {
                        calculateAndClipStartPosition(tabItem, iterator.previous());

                        if (tabItem.getIndex() == getSelectedTabIndex() || tabItem.isVisible()) {
                            viewRecycler.inflate(tabItem);
                            View view = tabItem.getView();

                            if (!ViewCompat.isLaidOut(view)) {
                                view.getViewTreeObserver().addOnGlobalLayoutListener(
                                        new LayoutListenerWrapper(view,
                                                createShowSwitcherLayoutListener(tabItem)));
                            } else {
                                animateShowSwitcher(tabItem);
                            }
                        }
                    }
                }
            }

        });
    }

    // TODO: Calling this method should also work when the view is not yet inflated
    @Override
    public final void hideSwitcher() {
        enqueuePendingAction(new Runnable() {

            @Override
            public void run() {
                if (isSwitcherShown()) {
                    setSwitcherShown(false);
                    tabViewBottomMargin = -1;
                    recyclerAdapter.clearCachedPreviews();
                    Iterator iterator =
                            new Iterator.Builder(getTabSwitcher(), viewRecycler).create();
                    TabItem tabItem;

                    while ((tabItem = iterator.next()) != null) {
                        if (tabItem.isInflated()) {
                            animateHideSwitcher(tabItem);
                        } else if (tabItem.getIndex() == getSelectedTabIndex()) {
                            inflateView(tabItem, createHideSwitcherLayoutListener(tabItem));
                        }
                    }
                }
            }

        });
    }

    @Override
    public final void selectTab(@NonNull final Tab tab) {
        ensureNotNull(tab, "The tab may not be null");
        enqueuePendingAction(new Runnable() {

            @Override
            public void run() {
                int index = indexOfOrThrowException(tab);
                setSelectedTabIndex(index);

                if (!isSwitcherShown()) {
                    viewRecycler.remove(TabItem
                            .create(getTabSwitcher(), viewRecycler, getSelectedTabIndex()));
                    viewRecycler.inflate(TabItem.create(getTabSwitcher(), viewRecycler, index));
                } else {
                    hideSwitcher();
                }
            }

        });
    }

    @NonNull
    @Override
    public final ViewGroup getTabContainer() {
        return tabContainer;
    }

    @NonNull
    @Override
    public final Toolbar getToolbar() {
        return toolbar;
    }

    @Override
    public final void onGlobalLayout() {
        ViewUtil.removeOnGlobalLayoutListener(getTabSwitcher().getViewTreeObserver(), this);

        if (getSelectedTabIndex() != -1) {
            TabItem tabItem = TabItem.create(getTabSwitcher(), viewRecycler, getSelectedTabIndex());
            viewRecycler.inflate(tabItem);
        }
    }

    @Override
    public final void onClick(@NonNull final TabItem tabItem) {
        selectTab(tabItem.getTab());
    }

    @Override
    public final void onCancelFling() {
        if (flingAnimation != null) {
            flingAnimation.cancel();
            flingAnimation = null;
            dragHandler.handleRelease(null);
        }
    }

    @Override
    public final void onFling(final float distance, final long duration) {
        animateFling(distance, duration);
    }

    @Override
    public final void onRevertStartOvershoot(final float maxAngle) {
        animateRevertStartOvershoot(maxAngle);
    }

    @Override
    public final void onRevertEndOvershoot(final float maxAngle) {
        animateRevertEndOvershoot(maxAngle);
    }

    public final void onStartOvershoot(final float position) {
        startOvershoot(position);
    }

    @Override
    public final void onTiltOnStartOvershoot(final float angle) {
        tiltOnStartOvershoot(angle);
    }

    @Override
    public final void onTiltOnEndOvershoot(final float angle) {
        tiltOnEndOvershoot(angle);
    }

    @Override
    public final void onSwipe(@NonNull final TabItem tabItem, final float distance) {
        swipe(tabItem, distance);
    }

    @Override
    public final void onSwipeEnded(@NonNull final TabItem tabItem, final boolean remove,
                                   final float velocity) {
        animateSwipe(tabItem, remove, velocity, 0, null,
                remove ? createRemoveAnimationListener(tabItem) :
                        createSwipeAnimationListener(tabItem));
    }

    @Override
    public final void onViewStateChanged(@NonNull final TabItem tabItem) {
        inflateOrRemoveView(tabItem);
    }

}