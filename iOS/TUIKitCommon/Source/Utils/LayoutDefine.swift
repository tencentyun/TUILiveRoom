//
//  LayoutDefine.swift
//  TXLiteAVDemo
//
//  Created by gg on 2021/3/15.
//  Copyright © 2021 Tencent. All rights reserved.
//

import Foundation
import UIKit

public let ScreenWidth = UIScreen.main.bounds.width
public let ScreenHeight = UIScreen.main.bounds.height

public let kDeviceIsIphoneX : Bool = {
    if UIDevice.current.userInterfaceIdiom == .pad {
        return false
    }
    let size = UIScreen.main.bounds.size
    let notchValue = Int(size.width/size.height*100)
    if notchValue == 216 || notchValue == 46 {
        return true
    }
    return false
}()

public let kDeviceSafeTopHeight : CGFloat = {
    if kDeviceIsIphoneX {
        return 44
    }
    else {
        return 20
    }
}()

public let kDeviceSafeBottomHeight : CGFloat = {
    if kDeviceIsIphoneX {
        return 34
    }
    else {
        return 0
    }
}()

public func convertPixel(w:CGFloat) -> CGFloat {
    return w/375.0*ScreenWidth
}

public func convertPixel(h:CGFloat) -> CGFloat {
    return h/812.0*ScreenHeight
}

public func statusBarHeight() -> CGFloat {
    var statusBarHeight: CGFloat = 0
    if #available(iOS 13.0, *) {
        let scene = UIApplication.shared.connectedScenes.first
        guard let windowScene = scene as? UIWindowScene else { return 0 }
        guard let statusBarManager = windowScene.statusBarManager else { return 0 }
        statusBarHeight = statusBarManager.statusBarFrame.height
    } else {
        statusBarHeight = UIApplication.shared.statusBarFrame.height
    }
    return statusBarHeight
}

public func navigationBarHeight() -> CGFloat {
    return 44.0
}

public func navigationFullHeight() -> CGFloat {
    return statusBarHeight() + navigationBarHeight()
}
