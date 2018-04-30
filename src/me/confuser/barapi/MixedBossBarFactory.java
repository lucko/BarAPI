/*
 * This file is part of BarAPI, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.confuser.barapi;

import me.lucko.helper.bossbar.BossBar;
import me.lucko.helper.bossbar.BossBarColor;
import me.lucko.helper.bossbar.BossBarFactory;
import me.lucko.helper.bossbar.BossBarStyle;
import org.bukkit.entity.Player;
import us.myles.ViaVersion.api.Via;
import us.myles.ViaVersion.api.ViaAPI;
import us.myles.ViaVersion.api.protocol.ProtocolVersion;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class MixedBossBarFactory implements BossBarFactory {

    @SuppressWarnings("unchecked")
    private final ViaAPI<Player> viaApi = Via.getAPI();

    // used for players on 1.8
    private final BarAPI legacyFactory;
    // used for players on 1.9+
    private final BossBarFactory newFactory;

    public MixedBossBarFactory(BarAPI legacyFactory, BossBarFactory newFactory) {
        this.legacyFactory = legacyFactory;
        this.newFactory = newFactory;
    }

    @Nonnull
    @Override
    public BossBar newBossBar() {
        return new MixedBossBar(this.viaApi, this.legacyFactory.newBossBar(), this.newFactory.newBossBar());
    }

    private static final class MixedBossBar implements BossBar {
        private final ViaAPI<Player> viaApi;
        private final BossBar legacyBar;
        private final BossBar newBar;

        private MixedBossBar(ViaAPI<Player> viaApi, BossBar legacyBar, BossBar newBar) {
            this.viaApi = viaApi;
            this.legacyBar = legacyBar;
            this.newBar = newBar;
        }

        @Nonnull
        @Override
        public String title() {
            return this.newBar.title();
        }

        @Nonnull
        @Override
        public BossBar title(@Nonnull String title) {
            this.legacyBar.title(title);
            this.newBar.title(title);
            return this;
        }

        @Override
        public double progress() {
            return this.newBar.progress();
        }

        @Nonnull
        @Override
        public BossBar progress(double progress) {
            this.legacyBar.progress(progress);
            this.newBar.progress(progress);
            return this;
        }

        @Nonnull
        @Override
        public BossBarColor color() {
            return this.newBar.color();
        }

        @Nonnull
        @Override
        public BossBar color(@Nonnull BossBarColor color) {
            this.legacyBar.color(color);
            this.newBar.color(color);
            return this;
        }

        @Nonnull
        @Override
        public BossBarStyle style() {
            return this.newBar.style();
        }

        @Nonnull
        @Override
        public BossBar style(@Nonnull BossBarStyle style) {
            this.legacyBar.style(style);
            this.newBar.style(style);
            return this;
        }

        @Override
        public boolean visible() {
            return this.newBar.visible();
        }

        @Nonnull
        @Override
        public BossBar visible(boolean visible) {
            this.legacyBar.visible(visible);
            this.newBar.visible(visible);
            return this;
        }

        @Nonnull
        @Override
        public List<Player> players() {
            List<Player> ret = new ArrayList<>();
            ret.addAll(this.legacyBar.players());
            ret.addAll(this.newBar.players());
            return ret;
        }

        @Override
        public void addPlayer(@Nonnull Player player) {
            int version = this.viaApi.getPlayerVersion(player);
            if (version < ProtocolVersion.v1_9.getId()) {
                this.legacyBar.addPlayer(player);
            } else {
                this.newBar.addPlayer(player);
            }
        }

        @Override
        public void removePlayer(@Nonnull Player player) {
            int version = this.viaApi.getPlayerVersion(player);
            if (version < ProtocolVersion.v1_9.getId()) {
                this.legacyBar.removePlayer(player);
            } else {
                this.newBar.removePlayer(player);
            }
        }

        @Override
        public void removeAll() {
            this.legacyBar.removeAll();
            this.newBar.removeAll();
        }

        @Override
        public void close() {
            this.legacyBar.close();
            this.newBar.close();
        }
    }
}
