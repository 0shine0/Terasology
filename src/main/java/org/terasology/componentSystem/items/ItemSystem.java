/*
 * Copyright 2012 Benjamin Glatzel <benjamin.glatzel@me.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.componentSystem.items;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;

import org.terasology.asset.AssetType;
import org.terasology.asset.AssetUri;
import org.terasology.components.HealthComponent;
import org.terasology.components.InventoryComponent;
import org.terasology.components.ItemComponent;
import org.terasology.components.LocalPlayerComponent;
import org.terasology.math.TeraMath;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.BlockItemComponent;
import org.terasology.entitySystem.EntityManager;
import org.terasology.entitySystem.EntityRef;
import org.terasology.entitySystem.EventHandlerSystem;
import org.terasology.entitySystem.EventPriority;
import org.terasology.entitySystem.ReceiveEvent;
import org.terasology.entitySystem.RegisterComponentSystem;
import org.terasology.entitySystem.event.RemovedComponentEvent;
import org.terasology.events.ActivateEvent;
import org.terasology.game.CoreRegistry;
import org.terasology.input.binds.UseItemButton;
import org.terasology.logic.manager.AudioManager;
import org.terasology.logic.manager.GUIManager;
import org.terasology.math.Side;
import org.terasology.math.Vector3i;
import org.terasology.physics.BulletPhysics;
import org.terasology.physics.CollisionGroup;
import org.terasology.physics.StandardCollisionGroup;
import org.terasology.rendering.gui.widgets.UIImage;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.family.BlockFamily;

import com.google.common.collect.Lists;

/**
 * TODO: Refactor use methods into events? Usage should become a separate component
 *
 * @author Immortius <immortius@gmail.com>
 */
@RegisterComponentSystem
public class ItemSystem implements EventHandlerSystem {
    private EntityManager entityManager;
    private WorldProvider worldProvider;
    private BlockEntityRegistry blockEntityRegistry;

    @Override
    public void initialise() {
        entityManager = CoreRegistry.get(EntityManager.class);
        worldProvider = CoreRegistry.get(WorldProvider.class);
        blockEntityRegistry = CoreRegistry.get(BlockEntityRegistry.class);
    }

    @Override
    public void shutdown() {
    }

    @ReceiveEvent(components = BlockItemComponent.class)
    public void onDestroyed(RemovedComponentEvent event, EntityRef entity) {
        entity.getComponent(BlockItemComponent.class).placedEntity.destroy();
    }

    @ReceiveEvent(components = {BlockItemComponent.class, ItemComponent.class})
    public void onPlaceBlock(ActivateEvent event, EntityRef item) {
        BlockItemComponent blockItem = item.getComponent(BlockItemComponent.class);

        Side surfaceDir = Side.inDirection(event.getHitNormal());
        Side secondaryDirection = TeraMath.getSecondaryPlacementDirection(event.getDirection(), event.getHitNormal());

        if (!placeBlock(blockItem.blockFamily, event.getTarget().getComponent(BlockComponent.class).getPosition(), surfaceDir, secondaryDirection, blockItem)) {
            event.cancel();
        }
    }

    @ReceiveEvent(components = ItemComponent.class, priority = EventPriority.PRIORITY_CRITICAL)
    public void checkCanUseItem(ActivateEvent event, EntityRef item) {
        ItemComponent itemComp = item.getComponent(ItemComponent.class);
        switch (itemComp.usage) {
            case NONE:
                event.cancel();
                break;
            case ON_BLOCK:
                if (event.getTarget().getComponent(BlockComponent.class) == null) {
                    event.cancel();
                }
                break;
            case ON_ENTITY:
                if (event.getTarget().getComponent(BlockComponent.class) != null) {
                    event.cancel();
                }
                break;
        }
    }

    @ReceiveEvent(components = ItemComponent.class, priority = EventPriority.PRIORITY_TRIVIAL)
    public void usedItem(ActivateEvent event, EntityRef item) {
        ItemComponent itemComp = item.getComponent(ItemComponent.class);
        if (itemComp.consumedOnUse) {
            itemComp.stackCount--;
            if (itemComp.stackCount == 0) {
                item.destroy();
            } else {
                item.saveComponent(itemComp);
            }
        }
    }

    /**
     * Places a block of a given type in front of the player.
     *
     * @param type The type of the block
     * @return True if a block was placed
     */
    private boolean placeBlock(BlockFamily type, Vector3i targetBlock, Side surfaceDirection, Side secondaryDirection, BlockItemComponent blockItem) {
        if (type == null)
            return true;

        Vector3i placementPos = new Vector3i(targetBlock);
        placementPos.add(surfaceDirection.getVector3i());

        Block block = type.getBlockFor(surfaceDirection, secondaryDirection);
        if (block == null)
            return false;

        if (canPlaceBlock(block, targetBlock, placementPos)) {
            if (blockEntityRegistry.setBlock(placementPos, block, worldProvider.getBlock(placementPos), blockItem.placedEntity)) {
                AudioManager.play(new AssetUri(AssetType.SOUND, "engine:PlaceBlock"), 0.5f);
                if (blockItem.placedEntity.exists()) {
                    blockItem.placedEntity = EntityRef.NULL;
                }
                return true;
            }
        }
        return false;
    }
    
    @ReceiveEvent(components = {LocalPlayerComponent.class,InventoryComponent.class} , priority=EventPriority.PRIORITY_TRIVIAL )
    public void charge(UseItemButton event, EntityRef player){
    	InventoryComponent inventory = player.getComponent(InventoryComponent.class);
        LocalPlayerComponent localPlayerComp = player.getComponent(LocalPlayerComponent.class);
        EntityRef selectedItemEntity = inventory.itemSlots.get(localPlayerComp.selectedTool);
        ItemComponent itemComponent = selectedItemEntity.getComponent(ItemComponent.class);
       	if( itemComponent != null && itemComponent.chargeable){
        	if(event.isDown()){
        		event.consume();
        		if(itemComponent.chargeTime < itemComponent.maxChargeTime){
        			itemComponent.chargeTime += event.getDelta();
        		}else{
        			itemComponent.chargeTime = itemComponent.maxChargeTime;
        		}
        	}else{
        		itemComponent.chargeTime = 0;
        	}
    		chargeUI(event, selectedItemEntity);
        }
    	selectedItemEntity.saveComponent(itemComponent);
    }
    
    public void chargeUI(UseItemButton event, EntityRef item){
      ItemComponent itemComponent = item.getComponent(ItemComponent.class);
      	if(itemComponent.chargeable){
      		UIImage crossHair = (UIImage)CoreRegistry.get(GUIManager.class).getWindowById("hud").getElementById("crosshair");
  			crossHair.getTextureOrigin().set(new Vector2f(22f / 256f, 22f / 256f));
      		if(event.isDown()){
      			float chargeTime  = (float)Math.floor(itemComponent.chargeTime/(itemComponent.maxChargeTime/6));
      			if(chargeTime > 6){
      				chargeTime = 6;
      			}
          		crossHair.getTextureOrigin().set(new Vector2f((46f + 22f*chargeTime) / 256f, 23f / 256f))	;
      		}else{
      			crossHair.getTextureSize().set(new Vector2f(20f / 256f, 20f / 256f));
      			crossHair.getTextureOrigin().set(new Vector2f(24f / 256f, 24f / 256f));
      		}	
      	}
    }

    private boolean canPlaceBlock(Block block, Vector3i targetBlock, Vector3i blockPos) {
        Block centerBlock = worldProvider.getBlock(targetBlock.x, targetBlock.y, targetBlock.z);

        if (!centerBlock.isAttachmentAllowed()) {
            return false;
        }

        Block adjBlock = worldProvider.getBlock(blockPos.x, blockPos.y, blockPos.z);
        if (!adjBlock.isReplacementAllowed() || adjBlock.isTargetable()) {
            return false;
        }

        // Prevent players from placing blocks inside their bounding boxes
        if (!block.isPenetrable()) {
            return !CoreRegistry.get(BulletPhysics.class).scanArea(block.getBounds(blockPos), Lists.<CollisionGroup>newArrayList(StandardCollisionGroup.DEFAULT, StandardCollisionGroup.CHARACTER)).iterator().hasNext();
        }
        return true;
    }
}
